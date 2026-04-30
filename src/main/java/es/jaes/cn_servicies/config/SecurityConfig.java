package es.jaes.cn_servicies.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.jaes.cn_servicies.auth.JwtAuthFilter;
import es.jaes.cn_servicies.auth.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, UserDetailsServiceImpl userDetailsService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(POST, "/api/auth/signup/with-role").permitAll()
                        .requestMatchers(GET, "/api/posts/published/**").permitAll()
                        .requestMatchers(GET, "/api/images/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(POST, "/api/posts/*/comments").authenticated()
                        .requestMatchers(GET, "/api/posts/*/comments").authenticated()
                        .requestMatchers(POST, "/api/posts/**").hasRole("EDITOR")
                        .requestMatchers(PUT, "/api/posts/**").hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(DELETE, "/api/posts/**").hasRole("ADMIN")
                        .requestMatchers(GET, "/api/users/me").authenticated()
                        .requestMatchers(POST, "/api/users/*/profile-photo").authenticated()
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers(GET, "/api/athletes/**").hasAnyRole("TECHNICAL_STAFF")
                        .requestMatchers(POST, "/api/athletes/**").hasAnyRole("TECHNICAL_STAFF")
                        .requestMatchers(PUT, "/api/athletes/**").hasAnyRole("TECHNICAL_STAFF")
                        .requestMatchers(DELETE, "/api/athletes/**").hasRole("ADMIN")
                        .requestMatchers(GET, "/api/competition-results/**").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers(POST, "/api/competition-results/**").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers(PUT, "/api/competition-results/**").hasAnyRole("ADMIN", "TECHNICAL_STAFF")
                        .requestMatchers(DELETE, "/api/competition-results/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler((request, response, e) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            new ObjectMapper().writeValue(response.getWriter(), Map.of(
                                    "timestamp", LocalDateTime.now().toString(),
                                    "status", 403,
                                    "message", "No tienes los permisos necesarios para acceder a este recurso",
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
                                    "message", "Debes autenticarte para acceder a este recurso. Incluye un token válido en la cabecera Authorization",
                                    "path", request.getRequestURI()
                            ));
                        })
                )
                .userDetailsService(userDetailsService)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}