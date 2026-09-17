package es.jaes.cn_servicies.training_group;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Permisos sobre los grupos.
 *
 * <p>El entrenador tiene que poder ver los grupos —el suyo, para empezar— pero
 * no montarlos ni duplicarlos. La duplicacion tiene matcher propio en
 * {@code SecurityConfig}, asi que necesita su comprobacion aparte: una ruta mal
 * escrita ahi dejaria a cualquiera crear catorce grupos de golpe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrainingGroupEndpointTest {

    private static final UUID CLUB = UUID.fromString("44444444-0000-0000-0000-000000000044");
    private static final String SLUG = "club-grupos-ep-it";
    private static final String CLAVE = "clave-de-prueba-it";

    private static final String ADMIN = "admin_grupoep_it";
    private static final String ENTRENADOR = "staff_grupoep_it";

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID temporada;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Grupos EP IT', ?, true, now())", CLUB, SLUG);
        crearUsuario(ADMIN, "ROLE_ADMIN");
        crearUsuario(ENTRENADOR, "ROLE_TECHNICAL_STAFF");

        temporada = UUID.randomUUID();
        jdbc.update("INSERT INTO seasons"
                        + " (id, club_id, name, start_date, end_date, active, created_at, updated_at)"
                        + " VALUES (?, ?, '2026/2027', DATE '2026-09-01', DATE '2027-08-31',"
                        + "  true, now(), now())",
                temporada, CLUB);
        // Lo lleva el entrenador: desde la S.3.3.b solo ve los grupos que lleva.
        jdbc.update("INSERT INTO training_groups"
                        + " (id, club_id, season_id, coach_id, name, category, level, created_at, updated_at)"
                        + " SELECT ?, ?, ?, u.id, 'Alevín A', 'ALEVIN', 'COMPETICION', now(), now()"
                        + " FROM users u WHERE u.club_id = ? AND u.username = ?",
                UUID.randomUUID(), CLUB, temporada, CLUB, ENTRENADOR);
    }

    @AfterAll
    void fin() {
        modoPublico();
        borrarTodo();
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void borrarTodo() {
        jdbc.update("DELETE FROM training_groups WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM seasons WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN"
                + " (SELECT id FROM users WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM users WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM clubs WHERE id = ?", CLUB);
    }

    private void crearUsuario(String username, String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users"
                        + " (id, club_id, username, email, password_hash, blocked, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, false, now(), now())",
                id, CLUB, username, username + "@it.local", passwordEncoder.encode(CLAVE));
        jdbc.update("INSERT INTO user_roles (user_id, role_id)"
                + " SELECT ?, id FROM roles WHERE name = ?", id, rol);
    }

    // ----------------------------------------------------------------

    @Test
    @DisplayName("el entrenador ve los grupos de la temporada")
    void elEntrenadorVeLosGrupos() {
        ResponseEntity<String> respuesta =
                get("/api/groups?seasonId=" + temporada, ENTRENADOR);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("Alevín A");
    }

    @Test
    @DisplayName("el entrenador no puede crear grupos")
    void elEntrenadorNoCrea() {
        String cuerpo = "{\"seasonId\":\"" + temporada + "\",\"name\":\"Nuevo\","
                + "\"category\":\"ALEVIN\",\"level\":\"INICIACION\"}";

        assertThat(post("/api/groups", ENTRENADOR, cuerpo).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("el entrenador no puede duplicar una temporada entera")
    void elEntrenadorNoDuplica() {
        String cuerpo = "{\"fromSeasonId\":\"" + temporada + "\",\"toSeasonId\":\""
                + UUID.randomUUID() + "\"}";

        assertThat(post("/api/groups/duplication", ENTRENADOR, cuerpo).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("sin token no se entra")
    void sinTokenNoSeEntra() {
        assertThat(rest.getForEntity("/api/groups", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ----------------------------------------------------------------

    private ResponseEntity<String> get(String ruta, String username) {
        return rest.exchange(ruta, HttpMethod.GET,
                new HttpEntity<>(autorizacion(username)), String.class);
    }

    private ResponseEntity<String> post(String ruta, String username, String cuerpo) {
        HttpHeaders cabeceras = autorizacion(username);
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(ruta, HttpMethod.POST,
                new HttpEntity<>(cuerpo, cabeceras), String.class);
    }

    private HttpHeaders autorizacion(String username) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(iniciarSesion(username));
        return cabeceras;
    }

    private String iniciarSesion(String username) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        String cuerpo = "{\"clubSlug\":\"" + SLUG + "\",\"username\":\"" + username + "\",\"password\":\"" + CLAVE + "\"}";

        ResponseEntity<String> respuesta = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(cuerpo, cabeceras), String.class);

        assertThat(respuesta.getStatusCode())
                .as("no se pudo iniciar sesion como %s", username)
                .isEqualTo(HttpStatus.OK);

        return respuesta.getBody().replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}
