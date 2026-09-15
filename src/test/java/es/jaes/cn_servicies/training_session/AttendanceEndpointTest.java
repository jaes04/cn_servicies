package es.jaes.cn_servicies.training_session;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 2.3: la API de asistencia.
 *
 * <p>Dos cosas que no se ven desde el servicio. La primera, <b>quien puede pasar
 * lista</b>: es el trabajo del entrenador, asi que es la primera vez que
 * {@code TECHNICAL_STAFF} escribe datos de menores.
 *
 * <p>La segunda, <b>que el roster no filtre de mas</b>. Es el endpoint que va a
 * consumir el movil y va lleno de nombres de menores; que ademas saliera el DNI
 * o la fecha de nacimiento seria una fuga silenciosa, del tipo que nadie mira
 * hasta que alguien la mira. Ver {@code docs/rgpd.md} §3.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttendanceEndpointTest {

    private static final UUID CLUB = UUID.fromString("dddd4444-0000-0000-0000-0000000044dd");
    private static final String SLUG = "club-asistencia-api-it";

    private static final UUID CLUB_AJENO = UUID.fromString("dddd4444-0000-0000-0000-0000000055dd");
    private static final String SLUG_AJENO = "club-asistencia-api-ajeno-it";

    private static final String CLAVE = "clave-de-prueba-it";
    private static final String ADMIN = "admin_asist_it";
    private static final String ENTRENADOR = "coach_asist_it";
    private static final String SOCIO = "socio_asist_it";

    private static final String DNI_ANA = "20000001A";
    private static final LocalDate NACIMIENTO = LocalDate.of(2013, 4, 17);

    private static final LocalDate HOY = LocalDate.now();
    private static final LocalDate INICIO = HOY.minusMonths(3);
    private static final LocalDate FIN = HOY.plusMonths(8);
    private static final LocalDate MARTES = HOY.with(TemporalAdjusters.previous(DayOfWeek.TUESDAY));

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private final Map<String, String> tokens = new HashMap<>();

    private UUID grupo;
    private UUID ana;
    private UUID sesion;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        crearClub(CLUB, SLUG);
        crearClub(CLUB_AJENO, SLUG_AJENO);
        crearUsuario(ADMIN, "ROLE_ADMIN");
        crearUsuario(ENTRENADOR, "ROLE_TECHNICAL_STAFF");
        crearUsuario(SOCIO, "ROLE_USER");
    }

    @BeforeEach
    void datos() {
        modoPublico();
        vaciar(CLUB);
        grupo = montarGrupo(CLUB);
        // Desde la S.3.3.b solo pasa lista quien lleva el grupo.
        jdbc.update("UPDATE training_groups SET coach_id ="
                + " (SELECT id FROM users WHERE club_id = ? AND username = ?) WHERE id = ?",
                CLUB, ENTRENADOR, grupo);
        ana = crearAtleta(CLUB, "Ana", DNI_ANA);
        apuntar(ana, grupo);
        sesion = crearSesion(CLUB, grupo, MARTES);
    }

    @AfterAll
    void fin() {
        modoPublico();
        borrarTodo();
    }

    // ----------------------------------------------------------------
    //  1. Permisos
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el entrenador lee el roster y pasa lista: es su trabajo")
    void elEntrenadorPasaLista() {
        assertThat(get(ruta("/roster"), ENTRENADOR).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> guardado = put(ruta("/attendance"), lista(ana, "PRESENT"), ENTRENADOR);

        assertThat(guardado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(guardado.getBody())
                .contains("\"status\":\"PRESENT\"")
                .contains("\"registeredBy\":\"" + ENTRENADOR + "\"");
    }

    @Test
    @DisplayName("un socio sin rol técnico no ve el roster ni pasa lista")
    void elSocioNoEntra() {
        assertThat(get(ruta("/roster"), SOCIO).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(put(ruta("/attendance"), lista(ana, "PRESENT"), SOCIO).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("sin token no se llega al roster")
    void sinToken() {
        assertThat(rest.getForEntity(ruta("/roster"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ----------------------------------------------------------------
    //  2. Minimización — lo que el roster NO debe llevar
    // ----------------------------------------------------------------

    /**
     * El roster necesita el nombre para pasar lista. Nada más: ni el DNI, que es
     * un identificador oficial, ni la fecha de nacimiento.
     */
    @Test
    @DisplayName("el roster lleva el nombre del atleta, pero ni su DNI ni su fecha de nacimiento")
    void elRosterNoFiltraDeMas() {
        String cuerpo = get(ruta("/roster"), ENTRENADOR).getBody();

        assertThat(cuerpo).contains("Ana Nadadora");
        assertThat(cuerpo).as("el DNI no pinta nada aquí").doesNotContain(DNI_ANA);
        assertThat(cuerpo).as("ni la fecha de nacimiento").doesNotContain(NACIMIENTO.toString());
    }

    // ----------------------------------------------------------------
    //  3. Guardado y aislamiento
    // ----------------------------------------------------------------

    @Test
    @DisplayName("guardar devuelve el roster ya actualizado: el móvil no tiene que volver a pedirlo")
    void guardarDevuelveElRoster() {
        String cuerpo = put(ruta("/attendance"), lista(ana, "LATE"), ENTRENADOR).getBody();

        assertThat(cuerpo)
                .contains("\"athletes\"")
                .contains("\"status\":\"LATE\"")
                .contains("\"sessionId\"");
    }

    @Test
    @DisplayName("pasar lista marca la sesión como realizada")
    void marcaRealizada() {
        put(ruta("/attendance"), lista(ana, "PRESENT"), ENTRENADOR);

        assertThat(get("/api/sessions/" + sesion, ADMIN).getBody())
                .contains("\"status\":\"DONE\"");
    }

    // ----------------------------------------------------------------
    //  4. Exportación a CSV (2.4.b)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el entrenador descarga el CSV: quien necesita el informe es el cuerpo técnico")
    void elEntrenadorExporta() {
        put(ruta("/attendance"), lista(ana, "PRESENT"), ENTRENADOR);

        ResponseEntity<String> csv = get(exportacion(), ENTRENADOR);

        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(csv.getHeaders().getFirst("Content-Disposition"))
                .contains("attachment")
                .contains(".csv");
        assertThat(csv.getBody())
                .contains("Atleta;Sesiones")
                .contains("Ana Nadadora;1;1;0;0;0;0;100,0");
    }

    /**
     * Un CSV sale del sistema y ya no vuelve: se reenvía, acaba en una carpeta
     * compartida, en un correo. Lo que no salga aquí es lo único que seguro no
     * acaba en ningún sitio.
     */
    @Test
    @DisplayName("el CSV lleva nombre y números, pero ni DNI ni fecha de nacimiento")
    void elCsvNoLlevaDeMas() {
        put(ruta("/attendance"), lista(ana, "PRESENT"), ENTRENADOR);

        String csv = get(exportacion(), ENTRENADOR).getBody();

        assertThat(csv).contains("Ana Nadadora");
        assertThat(csv).as("el DNI no sale del sistema en un archivo").doesNotContain(DNI_ANA);
        assertThat(csv).doesNotContain(NACIMIENTO.toString());
    }

    @Test
    @DisplayName("un socio sin rol técnico no puede exportar")
    void elSocioNoExporta() {
        assertThat(get(exportacion(), SOCIO).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("sin token tampoco")
    void sinTokenNoExporta() {
        assertThat(rest.getForEntity(exportacion(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("el roster de una sesión de otro club es un 404")
    void sesionAjena() {
        UUID ajena = crearSesionAjena();

        assertThat(get("/api/sessions/" + ajena + "/roster", ADMIN).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("una lista vacía se rechaza en la validación")
    void listaVacia() {
        assertThat(put(ruta("/attendance"), "{\"entries\":[]}", ENTRENADOR).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ----------------------------------------------------------------
    //  Andamiaje
    // ----------------------------------------------------------------

    private String ruta(String sufijo) {
        return "/api/sessions/" + sesion + sufijo;
    }

    private String exportacion() {
        return "/api/reports/attendance/group/" + grupo + "/export"
                + "?from=" + INICIO + "&to=" + FIN;
    }

    private String lista(UUID atleta, String estado) {
        return "{\"entries\":[{\"athleteId\":\"" + atleta + "\",\"status\":\"" + estado + "\"}]}";
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void vaciar(UUID club) {
        jdbc.update("DELETE FROM attendance WHERE session_id IN"
                + " (SELECT id FROM training_sessions WHERE club_id = ?)", club);
        jdbc.update("DELETE FROM training_sessions WHERE club_id = ?", club);
        jdbc.update("DELETE FROM athlete_groups WHERE athlete_id IN"
                + " (SELECT id FROM athletes WHERE club_id = ?)", club);
        jdbc.update("DELETE FROM group_schedules WHERE group_id IN"
                + " (SELECT id FROM training_groups WHERE club_id = ?)", club);
        jdbc.update("DELETE FROM training_groups WHERE club_id = ?", club);
        jdbc.update("DELETE FROM athletes WHERE club_id = ?", club);
        jdbc.update("DELETE FROM seasons WHERE club_id = ?", club);
    }

    private void borrarTodo() {
        vaciar(CLUB);
        vaciar(CLUB_AJENO);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN"
                + " (SELECT id FROM users WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM users WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM clubs WHERE id IN (?, ?)", CLUB, CLUB_AJENO);
    }

    private void crearClub(UUID id, String slug) {
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, ?, ?, true, now())", id, "Club " + slug, slug);
    }

    private void crearUsuario(String usuario, String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users"
                        + " (id, club_id, username, email, password_hash, blocked, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, false, now(), now())",
                id, CLUB, usuario, usuario + "@it.local", passwordEncoder.encode(CLAVE));
        jdbc.update("INSERT INTO user_roles (user_id, role_id)"
                + " SELECT ?, id FROM roles WHERE name = ?", id, rol);
    }

    private UUID montarGrupo(UUID club) {
        UUID temporada = UUID.randomUUID();
        UUID grupoId = UUID.randomUUID();
        jdbc.update("INSERT INTO seasons"
                        + " (id, club_id, name, start_date, end_date, active, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, true, now(), now())",
                temporada, club, "Temporada " + club, INICIO, FIN);
        jdbc.update("INSERT INTO training_groups"
                        + " (id, club_id, season_id, name, category, level, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'Alevín A', 'ALEVIN', 'COMPETICION', now(), now())",
                grupoId, club, temporada);
        return grupoId;
    }

    private UUID crearAtleta(UUID club, String nombre, String dni) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO athletes"
                        + " (id, club_id, first_name, last_name, birth_date, dni, gender_id,"
                        + "  created_at, updated_at)"
                        + " SELECT ?, ?, ?, 'Nadadora', ?, ?, g.id, now(), now()"
                        + " FROM genders g WHERE g.name = 'FEMALE'",
                id, club, nombre, NACIMIENTO, dni);
        return id;
    }

    private void apuntar(UUID atleta, UUID grupoId) {
        jdbc.update("INSERT INTO athlete_groups (id, athlete_id, group_id, joined_on, created_at)"
                + " VALUES (?, ?, ?, ?, now())", UUID.randomUUID(), atleta, grupoId, INICIO);
    }

    private UUID crearSesion(UUID club, UUID grupoId, LocalDate fecha) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO training_sessions"
                        + " (id, club_id, group_id, schedule_id, session_date, start_time, end_time,"
                        + "  modality, status, created_at, updated_at)"
                        + " VALUES (?, ?, ?, NULL, ?, '18:00', '19:00', 'SWIMMING', 'SCHEDULED',"
                        + "  now(), now())",
                id, club, grupoId, fecha);
        return id;
    }

    private UUID crearSesionAjena() {
        modoPublico();
        vaciar(CLUB_AJENO);
        UUID grupoAjeno = montarGrupo(CLUB_AJENO);
        return crearSesion(CLUB_AJENO, grupoAjeno, MARTES);
    }

    private ResponseEntity<String> get(String ruta, String usuario) {
        return rest.exchange(ruta, HttpMethod.GET,
                new HttpEntity<>(autorizacion(usuario)), String.class);
    }

    private ResponseEntity<String> put(String ruta, String cuerpo, String usuario) {
        HttpHeaders cabeceras = autorizacion(usuario);
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(ruta, HttpMethod.PUT,
                new HttpEntity<>(cuerpo, cabeceras), String.class);
    }

    private HttpHeaders autorizacion(String usuario) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(tokens.computeIfAbsent(usuario, this::iniciarSesion));
        return cabeceras;
    }

    private String iniciarSesion(String usuario) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        String cuerpo = "{\"username\":\"" + usuario + "\",\"password\":\"" + CLAVE + "\"}";

        ResponseEntity<String> respuesta = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(cuerpo, cabeceras), String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta.getBody().replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}
