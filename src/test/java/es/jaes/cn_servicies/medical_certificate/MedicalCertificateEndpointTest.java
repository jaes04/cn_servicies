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

import java.time.LocalDate;
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
        return crearAtleta("33333331A");
    }

    private UUID crearAtleta(String dni) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO athletes"
                        + " (id, club_id, first_name, last_name, birth_date, dni, gender_id, created_at, updated_at)"
                        + " SELECT ?, ?, 'Atleta', 'CertEp', DATE '2013-03-03', ?, g.id, now(), now()"
                        + " FROM genders g WHERE g.name = 'FEMALE'",
                id, CLUB, dni);
        return id;
    }

    /** Un certificado de la temporada activa para ese atleta, anotado hoy. */
    private UUID certificadoPara(UUID atletaId) {
        modoPublico();
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO medical_certificates"
                        + " (id, club_id, athlete_id, season_id, issued_on, expires_on,"
                        + "  validated_by_id, validated_at, created_at)"
                        + " SELECT ?, ?, ?, s.id, CURRENT_DATE, s.end_date, u.id, now(), now()"
                        + " FROM users u JOIN seasons s ON s.club_id = u.club_id AND s.active"
                        + " WHERE u.club_id = ? AND u.username = ?",
                id, CLUB, atletaId, CLUB, ADMIN);
        return id;
    }

    private UUID temporadaActiva() {
        modoPublico();
        return jdbc.queryForObject("SELECT id FROM seasons WHERE club_id = ? AND active", UUID.class, CLUB);
    }

    private int certificadosCon(UUID id) {
        modoPublico();
        return jdbc.queryForObject("SELECT count(*) FROM medical_certificates WHERE id = ?", Integer.class, id);
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
    //  Corregir y borrar
    // ----------------------------------------------------------------
    //
    //  Cada test crea su propio atleta y su certificado: los de arriba cuentan
    //  con que el del fixture siga ahi, y el orden de los tests no esta fijado.

    @Test
    @DisplayName("el administrador corrige la fecha de emisión de un certificado")
    void elAdminCorrige() {
        UUID certificado = certificadoPara(crearAtleta("33333332B"));
        LocalDate otraFecha = LocalDate.now().minusDays(10);

        ResponseEntity<String> respuesta = put("/api/medical-certificates/" + certificado,
                cuerpo(otraFecha, temporadaActiva()), ADMIN);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("\"issuedOn\":\"" + otraFecha + "\"");
    }

    /**
     * La fecha futura ya la corta una anotacion del cuerpo, asi que un test con
     * fecha futura no demostraria que la correccion aplica la regla del servicio.
     * Esta la pilla solo el servicio: fecha pasada, pero posterior al final de
     * una temporada que ya acabo.
     */
    @Test
    @DisplayName("la corrección pasa la misma regla que el alta: emitido después de acabar la temporada es 400")
    void laCorreccionPasaLaMismaRegla() {
        UUID certificado = certificadoPara(crearAtleta("33333333C"));
        UUID pasada = UUID.randomUUID();
        modoPublico();
        jdbc.update("INSERT INTO seasons"
                        + " (id, club_id, name, start_date, end_date, active, created_at, updated_at)"
                        + " VALUES (?, ?, 'Temporada pasada cert', CURRENT_DATE - 500, CURRENT_DATE - 200,"
                        + "  false, now(), now())",
                pasada, CLUB);

        ResponseEntity<String> respuesta = put("/api/medical-certificates/" + certificado,
                cuerpo(LocalDate.now().minusDays(10), pasada), ADMIN);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).contains("posterior al final de la temporada");
    }

    @Test
    @DisplayName("el administrador borra un certificado y el nadador se queda sin cubrir")
    void elAdminBorra() {
        UUID atletaId = crearAtleta("33333334D");
        UUID certificado = certificadoPara(atletaId);
        assertThat(get("/api/medical-certificates/athlete/" + atletaId + "/status", ADMIN).getBody())
                .contains("VALID");

        assertThat(borrar("/api/medical-certificates/" + certificado, ADMIN).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(get("/api/medical-certificates/athlete/" + atletaId + "/status", ADMIN).getBody())
                .contains("MISSING");
    }

    /**
     * El que sostiene la regla nueva de {@code SecurityConfig}. Sin ella, PUT y
     * DELETE caerian en el {@code anyRequest().authenticated()} del final y
     * cualquier cuenta podria borrar el certificado de cualquier nadador.
     */
    @Test
    @DisplayName("el entrenador no corrige ni borra certificados, y el certificado sigue ahí")
    void elEntrenadorNoCorrigeNiBorra() {
        UUID certificado = certificadoPara(crearAtleta("33333335E"));

        assertThat(put("/api/medical-certificates/" + certificado,
                cuerpo(LocalDate.now(), temporadaActiva()), ENTRENADOR).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(borrar("/api/medical-certificates/" + certificado, ENTRENADOR).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(certificadosCon(certificado)).isEqualTo(1);
    }

    @Test
    @DisplayName("un certificado que no existe es un 404")
    void unoQueNoExiste() {
        String ruta = "/api/medical-certificates/" + UUID.randomUUID();

        assertThat(put(ruta, cuerpo(LocalDate.now(), temporadaActiva()), ADMIN).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(borrar(ruta, ADMIN).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ----------------------------------------------------------------

    private String cuerpo(LocalDate emitido, UUID temporadaId) {
        return "{\"issuedOn\":\"" + emitido + "\",\"seasonId\":\"" + temporadaId + "\"}";
    }

    private ResponseEntity<String> put(String ruta, String cuerpo, String username) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(iniciarSesion(username));
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(ruta, HttpMethod.PUT, new HttpEntity<>(cuerpo, cabeceras), String.class);
    }

    private ResponseEntity<String> borrar(String ruta, String username) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(iniciarSesion(username));
        return rest.exchange(ruta, HttpMethod.DELETE, new HttpEntity<>(cabeceras), String.class);
    }

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
