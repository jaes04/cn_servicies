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
 * Tarea 2.2: la API de sesiones.
 *
 * <p>Lo que se comprueba aqui y no en el servicio son los permisos, que en este
 * bloque no son los de siempre: <b>el entrenador puede cancelar</b> —es quien se
 * entera de que hoy no hay piscina— pero no genera el calendario ni añade
 * sesiones sueltas. Es la primera vez que el personal tecnico escribe algo.
 *
 * <p>Y que la generacion sigue siendo idempotente cuando se lanza por HTTP, que
 * es como la va a lanzar el job de la 2.2.b.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrainingSessionEndpointTest {

    private static final UUID CLUB = UUID.fromString("99999999-0000-0000-0000-000000000099");
    private static final String SLUG = "club-sesiones-api-it";

    private static final UUID CLUB_AJENO = UUID.fromString("99999999-0000-0000-0000-0000000000aa");
    private static final String SLUG_AJENO = "club-sesiones-api-ajeno-it";

    private static final String CLAVE = "clave-de-prueba-it";
    private static final String ADMIN = "admin_sesiones_it";
    private static final String ENTRENADOR = "coach_sesiones_it";

    private static final LocalDate HOY = LocalDate.now();
    private static final LocalDate INICIO = HOY.minusMonths(3);
    private static final LocalDate FIN = HOY.plusMonths(8);
    private static final LocalDate PRIMER_MARTES =
            HOY.with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
    private static final LocalDate FIN_RANGO = PRIMER_MARTES.plusWeeks(4).minusDays(1);

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private final Map<String, String> tokens = new HashMap<>();

    private UUID grupo;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Sesiones API IT', ?, true, now())", CLUB, SLUG);
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Ajeno API IT', ?, true, now())", CLUB_AJENO, SLUG_AJENO);
        crearUsuario(ADMIN, "ROLE_ADMIN");
        crearUsuario(ENTRENADOR, "ROLE_TECHNICAL_STAFF");
    }

    @BeforeEach
    void datos() {
        modoPublico();
        vaciar(CLUB);
        grupo = montarGrupoConHorario(CLUB, "Alevín A");
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
    @DisplayName("el entrenador cancela una sesión: es quien se entera de que hoy no hay piscina")
    void elEntrenadorCancela() {
        generar(ADMIN);
        String sesion = primeraSesion();

        ResponseEntity<String> respuesta = post(
                "/api/sessions/" + sesion + "/cancellation?reason=POOL_CLOSURE", null, ENTRENADOR);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody())
                .contains("\"status\":\"CANCELLED\"")
                .contains("\"cancellationReason\":\"POOL_CLOSURE\"");
    }

    @Test
    @DisplayName("el entrenador no genera el calendario ni añade sesiones sueltas")
    void elEntrenadorNoMonta() {
        assertThat(post(rutaGeneracion(), null, ENTRENADOR).getStatusCode())
                .as("generación")
                .isEqualTo(HttpStatus.FORBIDDEN);

        String puntual = "{\"date\":\"" + PRIMER_MARTES + "\",\"startTime\":\"10:00:00\","
                + "\"endTime\":\"13:00:00\",\"modality\":\"SWIMMING\"}";
        assertThat(post("/api/groups/" + grupo + "/sessions", puntual, ENTRENADOR).getStatusCode())
                .as("sesión puntual")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("el entrenador consulta el calendario de su grupo")
    void elEntrenadorConsulta() {
        generar(ADMIN);

        assertThat(get(rutaCalendario(), ENTRENADOR).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("sin token no se llega a las sesiones")
    void sinToken() {
        assertThat(rest.getForEntity(rutaCalendario(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ----------------------------------------------------------------
    //  2. Generación por HTTP
    // ----------------------------------------------------------------

    @Test
    @DisplayName("la generación devuelve cuántas creó")
    void generacion() {
        assertThat(generar(ADMIN).getBody())
                .contains("\"created\":4")
                .contains("\"alreadyExisted\":0");
    }

    /**
     * Es como la va a llamar el job de la 2.2.b: sobre rangos que se repiten
     * semana tras semana.
     */
    @Test
    @DisplayName("lanzarla dos veces no crea nada nuevo")
    void generacionIdempotentePorHttp() {
        generar(ADMIN);

        assertThat(generar(ADMIN).getBody())
                .contains("\"created\":0")
                .contains("\"alreadyExisted\":4");
    }

    // ----------------------------------------------------------------
    //  3. Aislamiento por HTTP
    // ----------------------------------------------------------------

    /**
     * La ruta lleva el id suelto, sin grupo: lo unico que devuelve 404 en vez de
     * la sesion ajena es la policy de RLS.
     */
    @Test
    @DisplayName("la sesión de otro club es un 404, aunque se pida por su id")
    void sesionAjena() {
        UUID ajena = crearSesionAjena();

        assertThat(get("/api/sessions/" + ajena, ADMIN).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/api/sessions/" + ajena + "/cancellation?reason=HOLIDAY", null, ADMIN)
                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ----------------------------------------------------------------
    //  Andamiaje
    // ----------------------------------------------------------------

    private String rutaCalendario() {
        return "/api/groups/" + grupo + "/sessions?from=" + INICIO + "&to=" + FIN;
    }

    private String rutaGeneracion() {
        return "/api/groups/" + grupo + "/sessions/generation?from=" + PRIMER_MARTES
                + "&to=" + FIN_RANGO;
    }

    private ResponseEntity<String> generar(String usuario) {
        return post(rutaGeneracion(), null, usuario);
    }

    /** El id de la primera sesión del calendario, sacado del cuerpo. */
    private String primeraSesion() {
        return get(rutaCalendario(), ADMIN).getBody()
                .replaceAll("^\\[\\{\"id\":\"([^\"]+)\".*", "$1");
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void vaciar(UUID club) {
        jdbc.update("DELETE FROM training_sessions WHERE club_id = ?", club);
        jdbc.update("DELETE FROM group_schedules WHERE group_id IN"
                + " (SELECT id FROM training_groups WHERE club_id = ?)", club);
        jdbc.update("DELETE FROM training_groups WHERE club_id = ?", club);
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

    private void crearUsuario(String usuario, String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users"
                        + " (id, club_id, username, email, password_hash, blocked, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, false, now(), now())",
                id, CLUB, usuario, usuario + "@it.local", passwordEncoder.encode(CLAVE));
        jdbc.update("INSERT INTO user_roles (user_id, role_id)"
                + " SELECT ?, id FROM roles WHERE name = ?", id, rol);
    }

    /** Temporada, grupo y un horario de los martes, todo a pelo. */
    private UUID montarGrupoConHorario(UUID club, String nombre) {
        UUID temporada = UUID.randomUUID();
        UUID grupoId = UUID.randomUUID();
        jdbc.update("INSERT INTO seasons"
                        + " (id, club_id, name, start_date, end_date, active, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, true, now(), now())",
                temporada, club, "Temporada " + nombre, INICIO, FIN);
        jdbc.update("INSERT INTO training_groups"
                        + " (id, club_id, season_id, name, category, level, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, 'ALEVIN', 'COMPETICION', now(), now())",
                grupoId, club, temporada, nombre);
        jdbc.update("INSERT INTO group_schedules"
                        + " (id, group_id, day_of_week, start_time, end_time, modality,"
                        + "  valid_from, valid_until, created_at, updated_at)"
                        + " VALUES (?, ?, 'TUESDAY', '18:00', '19:00', 'SWIMMING', ?, NULL,"
                        + "  now(), now())",
                UUID.randomUUID(), grupoId, INICIO);
        return grupoId;
    }

    private UUID crearSesionAjena() {
        modoPublico();
        vaciar(CLUB_AJENO);
        UUID grupoAjeno = montarGrupoConHorario(CLUB_AJENO, "Grupo ajeno");
        UUID sesion = UUID.randomUUID();
        jdbc.update("INSERT INTO training_sessions"
                        + " (id, club_id, group_id, schedule_id, session_date, start_time, end_time,"
                        + "  modality, status, created_at, updated_at)"
                        + " VALUES (?, ?, ?, NULL, ?, '18:00', '19:00', 'SWIMMING', 'SCHEDULED',"
                        + "  now(), now())",
                sesion, CLUB_AJENO, grupoAjeno, PRIMER_MARTES);
        return sesion;
    }

    private ResponseEntity<String> get(String ruta, String usuario) {
        return rest.exchange(ruta, HttpMethod.GET,
                new HttpEntity<>(autorizacion(usuario)), String.class);
    }

    private ResponseEntity<String> post(String ruta, String cuerpo, String usuario) {
        HttpHeaders cabeceras = autorizacion(usuario);
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(ruta, HttpMethod.POST,
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
