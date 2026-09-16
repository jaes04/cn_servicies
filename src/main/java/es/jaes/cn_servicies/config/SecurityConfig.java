package es.jaes.cn_servicies.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.jaes.cn_servicies.auth.JwtAuthFilter;
import es.jaes.cn_servicies.auth.UserDetailsServiceImpl;
import es.jaes.cn_servicies.tenant.TenantFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.http.HttpMethod.*;


@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final TenantFilter tenantFilter;
    private final UserDetailsServiceImpl userDetailsService;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          TenantFilter tenantFilter,
                          UserDetailsServiceImpl userDetailsService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.tenantFilter = tenantFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // El orden importa: la regla mas especifica va primero, o la
                        // siguiente se la come. signup/with-role recibe los roles en el
                        // cuerpo, asi que publico equivale a regalar ROLE_ADMIN.
                        .requestMatchers(POST, "/api/auth/signup/with-role").hasRole("ADMIN")
                        // Publico solo lo que tiene que serlo, nunca /api/auth/** entero:
                        // con el comodin, cualquier endpoint que se anada aqui nace abierto.
                        .requestMatchers(POST, "/api/auth/login").permitAll()
                        .requestMatchers(POST, "/api/auth/refresh").permitAll()
                        .requestMatchers(POST, "/api/auth/signup").permitAll()
                        .requestMatchers(GET, "/api/posts/published/**").permitAll()
                        .requestMatchers(GET, "/api/images/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(POST, "/api/posts/*/comments").authenticated()
                        .requestMatchers(GET, "/api/posts/*/comments").authenticated()
                        .requestMatchers(DELETE, "/api/posts/*/comments/**").authenticated()
                        .requestMatchers(PATCH, "/api/posts/*/comments/**").hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(POST, "/api/posts/**").hasRole("EDITOR")
                        .requestMatchers(PUT, "/api/posts/**").hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(DELETE, "/api/posts/**").hasAnyRole("ADMIN", "EDITOR")
                        // Listado completo y lectura por id: devuelven tambien los
                        // borradores y los borrados. Hasta la regla de denegar por
                        // defecto no tenian regla propia y caian en el authenticated()
                        // final: cualquier cuenta —un tutor, un atleta— leia noticias
                        // sin publicar. Lo publico va por /published, mas arriba.
                        // Va despues de las de comentarios, o se las comeria.
                        .requestMatchers(GET, "/api/posts/**").hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(GET, "/api/users/me").authenticated()
                        .requestMatchers(GET, "/api/users/*/comments").authenticated()
                        .requestMatchers(POST, "/api/users/*/profile-photo").authenticated()
                        // Cada uno cambia su contrasena. Va antes que la regla
                        // general —que es de administrador— o se la comeria, y
                        // nadie podria cambiar la suya. Fijar la de otra cuenta
                        // es /api/users/{id}/password y esa si es de ADMIN.
                        .requestMatchers(PUT, "/api/users/me/password").authenticated()
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers(GET, "/api/athletes/**").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers(POST, "/api/athletes/**").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers(PUT, "/api/athletes/**").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers(DELETE, "/api/athletes/**").hasRole("ADMIN")
                        .requestMatchers(GET, "/api/competition-results/me").authenticated()
                        .requestMatchers(GET, "/api/competition-results/**").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers(POST, "/api/competition-results/**").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers(PUT, "/api/competition-results/**").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers(DELETE, "/api/competition-results/**").hasRole("ADMIN")
                        .requestMatchers(POST, "/api/athlete-links/*/key").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers(GET, "/api/athlete-links/by-athlete/**").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers(POST, "/api/athlete-links/redeem").authenticated()
                        .requestMatchers(GET, "/api/athlete-links/my-athletes").authenticated()
                        // Los tutelados de quien pregunta: datos suyos, basta con
                        // estar autenticado. Funcionaba sin regla, por el
                        // authenticated() final; con denyAll necesita la suya.
                        .requestMatchers(GET, "/api/athlete-links/my-tutees").authenticated()
                        // Consentimientos. El orden importa dos veces aqui: /status
                        // va antes que el historial porque si no se lo come la
                        // regla de ADMIN, y la revocacion antes que nada por
                        // legibilidad.
                        //
                        // El entrenador ve el ESTADO —si puede sacar una foto—
                        // pero no el historial: quien firmo, cuando y con que
                        // papel es informacion del club, no suya. Registrar y
                        // revocar es solo de ADMIN mientras no exista un portal
                        // donde el tutor conteste por si mismo.
                        .requestMatchers(POST, "/api/consents/*/revocation").hasRole("ADMIN")
                        .requestMatchers(GET, "/api/consents/athlete/*/status").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers(GET, "/api/consents/athlete/**").hasRole("ADMIN")
                        .requestMatchers(POST, "/api/consents/athlete/**").hasRole("ADMIN")
                        // Sesiones sueltas, por su propio id. Cancelar lo puede
                        // el entrenador: es quien se entera de que hoy no hay
                        // piscina, y esperar al administrador deja la sesion
                        // marcada como celebrada cuando nadie se metio al agua.
                        // El resto de la rama nace cerrado a ADMIN.
                        .requestMatchers(POST, "/api/sessions/*/cancellation").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        // Reactivar va con cancelar: quien puede equivocarse
                        // tiene que poder deshacerlo sin esperar al club.
                        .requestMatchers(POST, "/api/sessions/*/reactivation").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        // Pasar lista es el trabajo del entrenador: es quien
                        // esta al borde de la piscina.
                        .requestMatchers(PUT, "/api/sessions/*/attendance").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers(GET, "/api/sessions/**").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers("/api/sessions/**").hasRole("ADMIN")
                        // Informes de asistencia. El entrenador los consulta:
                        // son sobre su trabajo diario y no llevan nada que no
                        // vea ya al pasar lista.
                        .requestMatchers(GET, "/api/reports/**").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers("/api/reports/**").hasRole("ADMIN")
                        // Calendario de excepciones. El entrenador lo consulta
                        // —necesita saber que dias no hay— pero declarar un
                        // festivo es del club: tumba las sesiones de todos los
                        // grupos a la vez.
                        .requestMatchers(GET, "/api/closures/**").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers("/api/closures/**").hasRole("ADMIN")
                        // Grupos: el entrenador los consulta —necesita ver el
                        // suyo— pero montarlos, editarlos y duplicarlos es del
                        // club. La duplicacion crea N filas de golpe: va antes
                        // que la regla general para que se lea sola.
                        .requestMatchers(POST, "/api/groups/duplication").hasRole("ADMIN")
                        .requestMatchers(GET, "/api/groups/**").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers("/api/groups/**").hasRole("ADMIN")
                        // Temporadas: el entrenador las consulta —necesita saber
                        // en cual esta trabajando— pero crearlas y activarlas es
                        // del club. No hay DELETE que proteger: no existe.
                        .requestMatchers(GET, "/api/seasons/**").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers("/api/seasons/**").hasRole("ADMIN")
                        // Certificados medicos. Mismo criterio que arriba: el
                        // entrenador ve si el nadador esta cubierto, porque lo
                        // necesita antes de meterlo al agua, pero las fechas y
                        // quien valido el papel son del club.
                        .requestMatchers(GET, "/api/medical-certificates/athlete/*/status").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers(GET, "/api/medical-certificates/athlete/**").hasRole("ADMIN")
                        .requestMatchers(POST, "/api/medical-certificates/athlete/**").hasRole("ADMIN")
                        // Corregir y borrar por id, y lo que se anada despues. Sin
                        // esta regla, PUT y DELETE caerian en el anyRequest() del
                        // final, que solo pide estar autenticado: cualquier socio
                        // podria borrar el certificado de cualquier nadador.
                        .requestMatchers("/api/medical-certificates/**").hasRole("ADMIN")
                        // Papeles entregados (bloque 3a). Mismo reparto que el
                        // certificado: el entrenador ve el estado y si el
                        // nadador puede viajar —de los atletas de sus grupos,
                        // eso lo acota AccessGuard—; registrar la entrega y el
                        // historial con fechas son del club.
                        .requestMatchers(GET, "/api/document-deliveries/athlete/*/status").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers(GET, "/api/document-deliveries/athlete/*/travel-permit").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers("/api/document-deliveries/**").hasRole("ADMIN")
                        .requestMatchers(POST, "/api/athlete-documents/athlete/**").authenticated()
                        .requestMatchers(GET, "/api/athlete-documents/athlete/**").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers(GET, "/api/athlete-documents/my").authenticated()
                        .requestMatchers(GET, "/api/athlete-documents/*/file").authenticated()
                        .requestMatchers(DELETE, "/api/athlete-documents/**").hasRole("ADMIN")
                        // DENEGAR POR DEFECTO. Lo que no tenga regla arriba no lo
                        // puede nadie, ni un administrador.
                        //
                        // Antes era authenticated(), y eso hacia que cada ruta nueva
                        // naciera abierta a cualquier cuenta: un tutor o un atleta
                        // incluidos. Paso de verdad dos veces: PUT y DELETE de
                        // certificados medicos, y la lectura de noticias sin publicar.
                        //
                        // Si una ruta nueva contesta 403 a todo el mundo, le falta su
                        // regla aqui. DenyByDefaultTest recorre todas las rutas y
                        // falla en cuanto una se queda sin ella.
                        .anyRequest().denyAll()
                )
                .headers(h -> h
                        // Una API que devuelve JSON no tiene que cargar nada ni
                        // dejarse incrustar. No protege a la aplicacion del
                        // frontend —esa CSP va en Cloudflare Pages, en su repositorio—,
                        // pero cierra lo que se abra directamente en el navegador desde
                        // la API: una respuesta de error, o un archivo de /api/images
                        // que no sea lo que dice ser. No afecta a un <img> del frontend:
                        // la CSP solo manda sobre el documento que la recibe.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        // Estas dos ya las ponia Spring por defecto. Van explicitas
                        // para que nadie las quite sin verlo.
                        .frameOptions(f -> f.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler((request, response, e) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            new ObjectMapper().writeValue(response.getWriter(), Map.of(
                                    "timestamp", LocalDateTime.now().toString(),
                                    "status", 403,
                                    "error", "Forbidden",
                                    "message", "No tienes los permisos necesarios para acceder a este recurso",
                                    "method", request.getMethod(),
                                    "path", request.getRequestURI()
                            ));
                        })
                        .authenticationEntryPoint((request, response, e) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            new ObjectMapper().writeValue(response.getWriter(), Map.of(
                                    "timestamp", LocalDateTime.now().toString(),
                                    "status", 401,
                                    "error", "Unauthorized",
                                    "message", "Token ausente o inválido. Incluye un Bearer token válido en la cabecera Authorization",
                                    "method", request.getMethod(),
                                    "path", request.getRequestURI()
                            ));
                        })
                )
                .userDetailsService(userDetailsService)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // Despues del de JWT: necesita el SecurityContext ya poblado
                // para saber de que club es la peticion.
                .addFilterAfter(tenantFilter, JwtAuthFilter.class)
                .build();
    }

    /**
     * BCrypt con coste 12, no con el 10 por defecto.
     *
     * <p>Cada punto dobla el trabajo de comprobar una contrasena, y eso es lo
     * que encarece probarlas a millones si un dia se filtra la tabla de
     * usuarios. Cuesta unos 250 ms por login, que en una pantalla de acceso no
     * se nota.
     *
     * <p><b>Las contrasenas ya guardadas siguen valiendo</b>: el coste va dentro
     * del propio hash, asi que un hash de coste 10 se verifica igual. Lo que no
     * hace esto es re-cifrarlas — se quedan a 10 hasta que cada uno cambie la
     * suya. Ver la tarea de rehash en el roadmap.
     */
    @Bean
    public PasswordEncoder passwordEncoder(
            @Value("${app.security.bcrypt-strength:12}") int fuerza) {
        return new BCryptPasswordEncoder(fuerza);
    }

    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}