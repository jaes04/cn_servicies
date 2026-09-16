package es.jaes.cn_servicies.config;

import es.jaes.cn_servicies.auth.LoginAttemptService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Denegar por defecto: lo que no tiene regla en {@code SecurityConfig} no lo
 * puede nadie.
 *
 * <p>La regla final era {@code authenticated()}, y cada ruta nueva nacia abierta
 * a cualquier cuenta. Paso de verdad dos veces antes de cambiarlo: el PUT y el
 * DELETE de certificados medicos, y la lectura de noticias sin publicar.
 *
 * <p><b>El test importante es {@link #ningunaRutaSeQuedaSinRegla}.</b> Con
 * {@code denyAll()} el riesgo cambia de lado: una ruta sin regla ya no queda
 * abierta, queda cerrada para todos, administrador incluido. Este test recorre
 * todas las rutas registradas con un usuario que tiene los cuatro roles, y
 * cualquier 403 es una ruta sin regla. Quien anada un controlador y se olvide de
 * SecurityConfig se entera aqui, y no el dia que el club no puede usarlo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DenyByDefaultTest {

    private static final UUID CLUB = UUID.fromString("dddd9999-0000-0000-0000-000000009944");
    private static final String SLUG = "club-deny-it";
    private static final String CLAVE = "clave-de-prueba-it";

    /** Con los cuatro roles: cualquier regla que no sea denyAll le deja pasar. */
    private static final String TODOS = "todos_roles_it";
    private static final String SOCIO = "socio_deny_it";

    /**
     * Cuantas rutas hay como poco. Si la enumeracion devolviera cero, el test
     * pasaria sin comprobar nada; esto lo impide.
     */
    private static final int MINIMO_DE_RUTAS = 100;

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LoginAttemptService loginAttemptService;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping rutas;

    private String tokenTodos;
    private String tokenSocio;

    @BeforeAll
    void inicio() {
        loginAttemptService.olvidarTodo();
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Deny IT', ?, true, now())", CLUB, SLUG);
        crearUsuario(TODOS, "ROLE_ADMIN", "ROLE_EDITOR", "ROLE_TECHNICAL_STAFF", "ROLE_USER");
        crearUsuario(SOCIO, "ROLE_USER");
        tokenTodos = iniciarSesion(TODOS);
        tokenSocio = iniciarSesion(SOCIO);
    }

    @AfterAll
    void fin() {
        loginAttemptService.olvidarTodo();
        modoPublico();
        borrarTodo();
    }

    // ----------------------------------------------------------------

    @Test
    @DisplayName("ninguna ruta registrada se queda sin regla: con todos los roles, ninguna da 403")
    void ningunaRutaSeQuedaSinRegla() {
        List<String> sinRegla = new ArrayList<>();
        int comprobadas = 0;

        for (RequestMappingInfo info : rutas.getHandlerMethods().keySet()) {
            Set<RequestMethod> metodos = info.getMethodsCondition().getMethods();
            Set<RequestMethod> aProbar = metodos.isEmpty() ? Set.of(RequestMethod.GET) : metodos;

            for (String patron : info.getPatternValues()) {
                if (!patron.startsWith("/api/")) {
                    continue;
                }
                String url = concreta(patron);
                for (RequestMethod metodo : aProbar) {
                    comprobadas++;
                    if (llamar(metodo, url, tokenTodos).getStatusCode() == HttpStatus.FORBIDDEN) {
                        sinRegla.add(metodo + " " + patron);
                    }
                }
            }
        }

        assertThat(comprobadas)
                .as("se han encontrado demasiado pocas rutas: el test no estaria comprobando nada")
                .isGreaterThanOrEqualTo(MINIMO_DE_RUTAS);
        assertThat(sinRegla)
                .as("estas rutas no tienen regla en SecurityConfig y denyAll las cierra para todos")
                .isEmpty();
    }

    @Test
    @DisplayName("una ruta sin regla está cerrada incluso con todos los roles")
    void unaRutaSinReglaEstaCerrada() {
        // Antes, con authenticated(), esta pasaba al controlador y daba 404.
        assertThat(llamar(RequestMethod.GET, "/api/esto-no-tiene-regla", tokenTodos).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("una cuenta cualquiera ya no lee las noticias sin publicar ni las borradas")
    void lasNoticiasSinPublicarNoLasLeeCualquiera() {
        assertThat(llamar(RequestMethod.GET, "/api/posts", tokenSocio).getStatusCode())
                .as("el listado completo, con borradores")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(llamar(RequestMethod.GET, "/api/posts/" + UUID.randomUUID(), tokenSocio).getStatusCode())
                .as("una noticia por id, publicada o no")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("lo publicado sigue siendo público")
    void loPublicadoSigueSiendoPublico() {
        assertThat(rest.getForEntity("/api/posts/published", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("cada cuenta sigue viendo sus tutelados")
    void losTuteladosPropios() {
        assertThat(llamar(RequestMethod.GET, "/api/athlete-links/my-tutees", tokenSocio).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ----------------------------------------------------------------

    /**
     * Una URL concreta para un patron: cada variable, un UUID inventado —las rutas
     * por id contestan 404 y no tocan nada— y cada comodin, un segmento cualquiera.
     */
    private static String concreta(String patron) {
        return java.util.Arrays.stream(patron.split("/"))
                .map(segmento -> segmento.startsWith("{") ? UUID.randomUUID().toString()
                        : segmento.contains("*") ? "x"
                        : segmento)
                .collect(Collectors.joining("/"));
    }

    /**
     * Las de escritura llevan un JSON roto a proposito: el filtro de seguridad
     * decide antes de leer el cuerpo, asi que un 403 sigue saliendo, pero si pasa,
     * Spring rechaza el cuerpo con 400 antes de llegar al controlador. El test
     * no crea ni cambia nada en la base.
     */
    private ResponseEntity<String> llamar(RequestMethod metodo, String url, String token) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(token);
        String cuerpo = null;
        if (metodo == RequestMethod.POST || metodo == RequestMethod.PUT || metodo == RequestMethod.PATCH) {
            cabeceras.setContentType(MediaType.APPLICATION_JSON);
            cuerpo = "{";
        }
        return rest.exchange(url, HttpMethod.valueOf(metodo.name()), new HttpEntity<>(cuerpo, cabeceras), String.class);
    }

    private void crearUsuario(String username, String... roles) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users"
                        + " (id, club_id, username, email, password_hash, blocked, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, false, now(), now())",
                id, CLUB, username, username + "@it.local", passwordEncoder.encode(CLAVE));
        for (String rol : roles) {
            jdbc.update("INSERT INTO user_roles (user_id, role_id)"
                    + " SELECT ?, id FROM roles WHERE name = ?", id, rol);
        }
    }

    private String iniciarSesion(String username) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        String cuerpo = "{\"username\":\"" + username + "\",\"password\":\"" + CLAVE + "\"}";
        ResponseEntity<String> respuesta = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(cuerpo, cabeceras), String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta.getBody().replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void borrarTodo() {
        jdbc.update("DELETE FROM user_roles WHERE user_id IN"
                + " (SELECT id FROM users WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM users WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM clubs WHERE id = ?", CLUB);
    }
}
