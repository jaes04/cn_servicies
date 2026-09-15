package es.jaes.cn_servicies.guardian;

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

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bloque 4 de la S.1: los endpoints de consentimiento.
 *
 * <p>Lo que se comprueba es <b>quien puede que</b>. El reparto no es evidente y
 * es el que hay que proteger de una regresion: el entrenador ve el estado
 * —necesita saber si puede publicar una foto— pero no el historial, porque
 * quien firmo y con que papel es informacion del club.
 *
 * <p>Y que revocar por HTTP no borra la fila, que es la propiedad de la que
 * depende que el registro sirva como prueba.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsentEndpointTest {

    private static final UUID CLUB = UUID.fromString("eeeeeeee-0000-0000-0000-00000000000e");
    private static final String SLUG = "club-endpoints-it";
    private static final String CLAVE = "clave-de-prueba-it";

    private static final String ADMIN = "admin_consent_it";
    private static final String ENTRENADOR = "staff_consent_it";
    private static final String SOCIO = "user_consent_it";

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID atleta;
    private UUID tutor;
    private UUID consentimientoDeImagen;

    // ----------------------------------------------------------------
    //  Datos de prueba
    // ----------------------------------------------------------------

    @BeforeAll
    void crearDatos() {
        modoPublico();
        borrarDatos();

        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Endpoints IT', ?, true, now())", CLUB, SLUG);
        crearUsuario(ADMIN, "ROLE_ADMIN");
        crearUsuario(ENTRENADOR, "ROLE_TECHNICAL_STAFF");
        crearUsuario(SOCIO, "ROLE_USER");

        atleta = crearAtleta("55555551A");
        tutor = crearTutor("55555552B");
        vincular(atleta, tutor);
        consentimientoDeImagen = crearConsentimiento("IMAGE");

        // Desde la S.3.3.b el entrenador solo ve el estado de los atletas que hoy
        // estan en sus grupos, asi que este atleta tiene que estar en uno suyo.
        ponerEnUnGrupoDelEntrenador(atleta);
    }

    @AfterAll
    void fin() {
        modoPublico();
        borrarDatos();
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void ponerEnUnGrupoDelEntrenador(UUID atletaId) {
        UUID temporada = UUID.randomUUID();
        UUID grupo = UUID.randomUUID();
        jdbc.update("INSERT INTO seasons"
                        + " (id, club_id, name, start_date, end_date, active, created_at, updated_at)"
                        + " VALUES (?, ?, 'Temporada consent', CURRENT_DATE - 90, CURRENT_DATE + 240,"
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

    private void borrarDatos() {
        jdbc.update("DELETE FROM consents WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM athlete_guardians WHERE guardian_id IN"
                + " (SELECT id FROM guardians WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM guardians WHERE club_id = ?", CLUB);
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

    private UUID crearAtleta(String dni) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO athletes"
                        + " (id, club_id, first_name, last_name, birth_date, dni, gender_id, created_at, updated_at)"
                        + " SELECT ?, ?, 'Atleta', 'Endpoints', DATE '2015-05-05', ?, g.id, now(), now()"
                        + " FROM genders g WHERE g.name = 'FEMALE'",
                id, CLUB, dni);
        return id;
    }

    private UUID crearTutor(String dni) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO guardians"
                        + " (id, club_id, first_name, last_name, dni, email, created_at, updated_at)"
                        + " VALUES (?, ?, 'Tutora', 'Endpoints', ?, ?, now(), now())",
                id, CLUB, dni, dni.toLowerCase() + "@it.local");
        return id;
    }

    private void vincular(UUID atletaId, UUID tutorId) {
        jdbc.update("INSERT INTO athlete_guardians"
                        + " (id, athlete_id, guardian_id, relationship, created_at)"
                        + " VALUES (?, ?, ?, 'MOTHER', now())",
                UUID.randomUUID(), atletaId, tutorId);
    }

    /**
     * El tipo es parametro para que cada test que revoque se cree el suyo. JUnit
     * no garantiza el orden, y compartir el consentimiento haria que el test del
     * estado pasara o fallara segun cuando cayera el de la revocacion.
     */
    private UUID crearConsentimiento(String tipo) {
        UUID id = UUID.randomUUID();
        modoPublico();
        jdbc.update("INSERT INTO consents"
                        + " (id, club_id, athlete_id, guardian_id, type, granted,"
                        + "  decision_date, evidence_type, evidence_ref, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, true, ?, 'PAPER_FORM', 'F-IT-1', now())",
                id, CLUB, atleta, tutor, tipo, LocalDate.now());
        return id;
    }

    /** Cuenta por JDBC y no por el repositorio: fuera de una peticion no hay club fijado y RLS no dejaria ver la fila. */
    private int filasDelConsentimiento(UUID id) {
        modoPublico();
        return jdbc.queryForObject("SELECT count(*) FROM consents WHERE id = ?", Integer.class, id);
    }

    // ----------------------------------------------------------------
    //  1. Quien ve el historial
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el administrador ve el historial con la evidencia")
    void elAdminVeElHistorial() {
        ResponseEntity<String> respuesta = get("/api/consents/athlete/" + atleta, ADMIN);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("IMAGE").contains("F-IT-1").contains("Tutora");
    }

    @Test
    @DisplayName("el entrenador NO ve el historial: quién firmó y con qué papel no es cosa suya")
    void elEntrenadorNoVeElHistorial() {
        assertThat(get("/api/consents/athlete/" + atleta, ENTRENADOR).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("un socio sin rol no ve nada de esto")
    void elSocioNoVeNada() {
        assertThat(get("/api/consents/athlete/" + atleta, SOCIO).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/consents/athlete/" + atleta + "/status", SOCIO).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("sin token no se entra")
    void sinTokenNoSeEntra() {
        assertThat(rest.getForEntity("/api/consents/athlete/" + atleta, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ----------------------------------------------------------------
    //  2. El estado sí lo ve el entrenador
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el entrenador ve el estado por finalidad, sin nombres ni evidencia")
    void elEntrenadorVeElEstado() {
        ResponseEntity<String> respuesta =
                get("/api/consents/athlete/" + atleta + "/status", ENTRENADOR);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("\"IMAGE\":true");
        assertThat(respuesta.getBody())
                .as("las finalidades sin contestar salen en false, no ausentes")
                .contains("\"DATA_PROCESSING\":false");
        assertThat(respuesta.getBody())
                .as("el estado no lleva quien firmo ni con que papel")
                .doesNotContain("Tutora").doesNotContain("F-IT-1");
    }

    // ----------------------------------------------------------------
    //  3. Revocar
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el entrenador no puede revocar")
    void elEntrenadorNoRevoca() {
        assertThat(post("/api/consents/" + consentimientoDeImagen + "/revocation", ENTRENADOR)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("revocar por HTTP quita la vigencia y deja la fila")
    void revocarDejaLaFila() {
        // El suyo propio, para no depender de en que orden caiga este test.
        UUID propio = crearConsentimiento("COMMUNICATIONS");

        ResponseEntity<String> respuesta = post("/api/consents/" + propio + "/revocation", ADMIN);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("\"active\":false");

        assertThat(filasDelConsentimiento(propio))
                .as("revocar no es borrar: la prueba de que existio tiene que quedar")
                .isEqualTo(1);
        assertThat(get("/api/consents/athlete/" + atleta + "/status", ENTRENADOR).getBody())
                .contains("\"COMMUNICATIONS\":false");
    }

    // ----------------------------------------------------------------
    //  Utilidades
    // ----------------------------------------------------------------

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

    private ResponseEntity<String> get(String ruta, String username) {
        return rest.exchange(ruta, HttpMethod.GET,
                new HttpEntity<>(autorizacion(username)), String.class);
    }

    private ResponseEntity<String> post(String ruta, String username) {
        return rest.exchange(ruta, HttpMethod.POST,
                new HttpEntity<>(autorizacion(username)), String.class);
    }

    private HttpHeaders autorizacion(String username) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(iniciarSesion(username));
        return cabeceras;
    }
}
