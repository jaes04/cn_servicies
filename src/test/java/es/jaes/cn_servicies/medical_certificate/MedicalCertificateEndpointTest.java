package es.jaes.cn_servicies.medical_certificate;

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
 * El reparto de permisos sobre los certificados medicos.
 *
 * <p>Es el mismo criterio que en los consentimientos, pero las reglas de
 * {@code SecurityConfig} van por ruta: una ruta mal escrita ahi dejaria el
 * historial abierto al entrenador sin que nada mas se rompiera. Por eso tiene
 * test propio y no se da por probado con el de consentimientos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MedicalCertificateEndpointTest {

    private static final UUID CLUB = UUID.fromString("11111111-0000-0000-0000-000000000011");
    private static final String SLUG = "club-cert-endpoints-it";
    private static final String CLAVE = "clave-de-prueba-it";

    private static final String ADMIN = "admin_certep_it";
    private static final String ENTRENADOR = "staff_certep_it";

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID atleta;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Cert Endpoints IT', ?, true, now())", CLUB, SLUG);
        crearUsuario(ADMIN, "ROLE_ADMIN");
        crearUsuario(ENTRENADOR, "ROLE_TECHNICAL_STAFF");
        atleta = crearAtleta();

        // Desde la S.3.3.b el entrenador solo ve el estado de los atletas que hoy
        // estan en sus grupos, asi que este atleta tiene que estar en uno suyo.
        // Va antes que el certificado porque crea la temporada activa, y desde el
        // bloque 3b cada certificado es de una.
        ponerEnUnGrupoDelEntrenador(atleta);
        crearCertificado();
    }

    @AfterAll
    void fin() {
        modoPublico();
        borrarTodo();
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void ponerEnUnGrupoDelEntrenador(UUID atletaId) {
        UUID temporada = UUID.randomUUID();
        UUID grupo = UUID.randomUUID();
        jdbc.update("INSERT INTO seasons"
                        + " (id, club_id, name, start_date, end_date, active, created_at, updated_at)"
                        + " VALUES (?, ?, 'Temporada cert', CURRENT_DATE - 90, CURRENT_DATE + 240,"
                        + "  true, now(), now())",
                temporada, CLUB);
        jdbc.update("INSERT INTO training_groups"
                        + " (id, club_id, season_id, coach_id, name, category, level, created_at, updated_at)"
                        + " SELECT ?, ?, ?, u.id, 'Alevín A', 'ALEVIN', 'COMPETICION', now(), now()"
                        + " FROM users u WHERE u.club_id = ? AND u.username = ?",
                grupo, CLUB, temporada, CLUB, ENTRENADOR);
        jdbc.update("INSERT INTO athlete_groups (id, athlete_id, group_id, joined_on, created_at)"
                        + " VALUES (?, ?, ?, CURRENT_DATE - 30, now())",
                UUID.randomUUID(), atletaId, grupo);
    }

    private void borrarTodo() {
        jdbc.update("DELETE FROM medical_certificates WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM athlete_groups WHERE athlete_id IN"
                + " (SELECT id FROM athletes WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM training_groups WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM seasons WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM athletes WHERE club_id = ?", CLUB);
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

    private UUID crearAtleta() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO athletes"
                        + " (id, club_id, first_name, last_name, birth_date, dni, gender_id, created_at, updated_at)"
                        + " SELECT ?, ?, 'Atleta', 'CertEp', DATE '2013-03-03', '33333331A', g.id, now(), now()"
                        + " FROM genders g WHERE g.name = 'FEMALE'",
                id, CLUB);
        return id;
    }

    private void crearCertificado() {
        jdbc.update("INSERT INTO medical_certificates"
                        + " (id, club_id, athlete_id, season_id, issued_on, expires_on,"
                        + "  validated_by_id, validated_at, created_at)"
                        + " SELECT ?, ?, ?, s.id, CURRENT_DATE, s.end_date, u.id, now(), now()"
                        + " FROM users u JOIN seasons s ON s.club_id = u.club_id AND s.active"
                        + " WHERE u.club_id = ? AND u.username = ?",
                UUID.randomUUID(), CLUB, atleta, CLUB, ADMIN);
    }

    // ----------------------------------------------------------------

    @Test
    @DisplayName("el administrador ve el historial con fechas y validador")
    void elAdminVeElHistorial() {
        ResponseEntity<String> respuesta = get("/api/medical-certificates/athlete/" + atleta, ADMIN);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("VALID").contains(ADMIN);
    }

    @Test
    @DisplayName("el entrenador NO ve el historial")
    void elEntrenadorNoVeElHistorial() {
        assertThat(get("/api/medical-certificates/athlete/" + atleta, ENTRENADOR).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("el entrenador sí ve si el nadador está cubierto, que es lo que necesita")
    void elEntrenadorVeElEstado() {
        ResponseEntity<String> respuesta =
                get("/api/medical-certificates/athlete/" + atleta + "/status", ENTRENADOR);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("VALID");
        assertThat(respuesta.getBody())
                .as("el estado no lleva fechas ni quién validó")
                .doesNotContain(ADMIN);
    }

    /**
     * Bloque 3c: la lista de caducidades se retiro, y la sustituye el informe de
     * documentacion pendiente. Quien la siga llamando tiene que recibir un 404, no
     * un 500 que parezca un fallo del servidor.
     */
    @Test
    @DisplayName("la lista de caducidades próximas ya no existe: 404, no 500")
    void laListaDeCaducidadesYaNoExiste() {
        assertThat(get("/api/medical-certificates/expiring", ADMIN).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ----------------------------------------------------------------

    private ResponseEntity<String> get(String ruta, String username) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(iniciarSesion(username));
        return rest.exchange(ruta, HttpMethod.GET, new HttpEntity<>(cabeceras), String.class);
    }

    private String iniciarSesion(String username) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        String cuerpo = "{\"username\":\"" + username + "\",\"password\":\"" + CLAVE + "\"}";

        ResponseEntity<String> respuesta = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(cuerpo, cabeceras), String.class);

        assertThat(respuesta.getStatusCode())
                .as("no se pudo iniciar sesion como %s", username)
                .isEqualTo(HttpStatus.OK);

        return respuesta.getBody().replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}
