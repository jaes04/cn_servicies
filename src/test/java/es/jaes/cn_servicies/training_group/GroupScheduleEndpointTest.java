package es.jaes.cn_servicies.training_group;

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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 2.1: la API de horarios.
 *
 * <p>Lo que se comprueba aqui y no en el servicio son <b>los permisos</b>: que
 * las rutas anidadas caigan de verdad bajo las reglas de {@code /api/groups/**}
 * que ya existian en {@code SecurityConfig}. Esa afirmacion —"no hace falta
 * tocar la configuracion de seguridad"— es facil de hacer y facil de que sea
 * mentira; este test es lo que la sostiene.
 *
 * <p>Lo demas que solo se ve por HTTP: que el solape salga como 400 y no como
 * 500, y que el grupo de otro club sea un 404 y no un 403 que confirme que
 * existe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupScheduleEndpointTest {

    private static final UUID CLUB = UUID.fromString("77777777-0000-0000-0000-000000000077");
    private static final String SLUG = "club-horarios-api-it";

    private static final UUID CLUB_AJENO = UUID.fromString("77777777-0000-0000-0000-0000000000aa");
    private static final String SLUG_AJENO = "club-horarios-api-ajeno-it";

    private static final String CLAVE = "clave-de-prueba-it";
    private static final String ADMIN = "admin_horarios_it";
    private static final String ENTRENADOR = "coach_horarios_it";

    /** Alrededor de hoy: el campo {@code inForce} se calcula contra la fecha actual. */
    private static final LocalDate HOY = LocalDate.now();
    private static final LocalDate INICIO = HOY.minusMonths(3);
    private static final LocalDate FIN = HOY.plusMonths(8);

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private final Map<String, String> tokens = new HashMap<>();

    private UUID grupo;
    private UUID grupoAjeno;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Horarios API IT', ?, true, now())", CLUB, SLUG);
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Ajeno API IT', ?, true, now())", CLUB_AJENO, SLUG_AJENO);
        crearUsuario(ADMIN, "ROLE_ADMIN");
        crearUsuario(ENTRENADOR, "ROLE_TECHNICAL_STAFF");
        grupoAjeno = crearGrupo(CLUB_AJENO, "Grupo ajeno");
    }

    @BeforeEach
    void datos() {
        modoPublico();
        vaciarHorariosYGrupos(CLUB);
        grupo = crearGrupo(CLUB, "Alevín A");
    }

    @AfterAll
    void fin() {
        modoPublico();
        borrarTodo();
    }

    // ----------------------------------------------------------------
    //  1. Permisos — lo que justifica no haber tocado SecurityConfig
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el entrenador consulta los horarios de un grupo")
    void elEntrenadorConsulta() {
        crearHorario("TUESDAY", "18:00:00", "19:00:00");

        ResponseEntity<String> respuesta = get(ruta(), ENTRENADOR);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("TUESDAY");
    }

    @Test
    @DisplayName("el entrenador no monta, no edita y no borra horarios: montarlos es del club")
    void elEntrenadorNoMonta() {
        String horario = crearHorario("TUESDAY", "18:00:00", "19:00:00");

        assertThat(post(ruta(), cuerpo("THURSDAY", "18:00:00", "19:00:00"), ENTRENADOR)
                .getStatusCode())
                .as("POST")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(put(ruta() + "/" + horario, cuerpo("TUESDAY", "17:00:00", "18:00:00"), ENTRENADOR)
                .getStatusCode())
                .as("PUT")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(delete(ruta() + "/" + horario, ENTRENADOR).getStatusCode())
                .as("DELETE")
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(get(ruta(), ADMIN).getBody())
                .as("y el horario sigue donde estaba")
                .contains("TUESDAY");
    }

    @Test
    @DisplayName("sin token no se llega a los horarios")
    void sinToken() {
        assertThat(rest.getForEntity(ruta(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ----------------------------------------------------------------
    //  2. El ciclo completo por HTTP
    // ----------------------------------------------------------------

    @Test
    @DisplayName("alta, consulta, edición y borrado de un horario")
    void cicloCompleto() {
        String id = crearHorario("TUESDAY", "18:00:00", "19:00:00");

        assertThat(get(ruta(), ADMIN).getBody())
                .contains("\"modality\":\"SWIMMING\"")
                .contains("\"inForce\":true");

        assertThat(put(ruta() + "/" + id, cuerpo("TUESDAY", "18:00:00", "19:30:00"), ADMIN))
                .satisfies(r -> {
                    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(r.getBody()).contains("19:30");
                });

        assertThat(delete(ruta() + "/" + id, ADMIN).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get(ruta(), ADMIN).getBody()).isEqualTo("[]");
    }

    @Test
    @DisplayName("la consulta por fecha devuelve los que estaban en vigor ese día")
    void porFecha() {
        post(ruta(), cuerpoConVigencia("TUESDAY", "18:00:00", "19:00:00", INICIO, HOY), ADMIN);

        assertThat(get(ruta() + "?date=" + HOY, ADMIN).getBody())
                .as("el día del cierre todavía está en vigor")
                .contains("TUESDAY");
        assertThat(get(ruta() + "?date=" + HOY.plusDays(1), ADMIN).getBody())
                .isEqualTo("[]");
    }

    // ----------------------------------------------------------------
    //  3. Errores
    // ----------------------------------------------------------------

    @Test
    @DisplayName("un horario que se solapa es 400, no un 500")
    void elSolapeEs400() {
        crearHorario("TUESDAY", "18:00:00", "19:00:00");

        ResponseEntity<String> respuesta =
                post(ruta(), cuerpo("TUESDAY", "18:30:00", "19:30:00"), ADMIN);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).contains("solapa");
    }

    @Test
    @DisplayName("un cuerpo sin modalidad se rechaza en la validación del DTO")
    void faltaLaModalidad() {
        String sinModalidad = "{\"dayOfWeek\":\"TUESDAY\",\"startTime\":\"18:00:00\","
                + "\"endTime\":\"19:00:00\",\"validFrom\":\"" + INICIO + "\"}";

        assertThat(post(ruta(), sinModalidad, ADMIN).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * 404 y no 403: quien pregunta por un grupo de otro club no tiene por que
     * enterarse de que existe.
     */
    @Test
    @DisplayName("los horarios de un grupo de otro club son un 404")
    void grupoDeOtroClub() {
        assertThat(get("/api/groups/" + grupoAjeno + "/schedules", ADMIN).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ----------------------------------------------------------------
    //  Andamiaje
    // ----------------------------------------------------------------

    private String ruta() {
        return "/api/groups/" + grupo + "/schedules";
    }

    private String cuerpo(String dia, String desde, String hasta) {
        return cuerpoConVigencia(dia, desde, hasta, INICIO, null);
    }

    private String cuerpoConVigencia(String dia, String desde, String hasta,
                                     LocalDate vigenteDesde, LocalDate vigenteHasta) {
        return "{\"dayOfWeek\":\"" + dia + "\","
                + "\"startTime\":\"" + desde + "\","
                + "\"endTime\":\"" + hasta + "\","
                + "\"modality\":\"SWIMMING\","
                + "\"validFrom\":\"" + vigenteDesde + "\","
                + "\"validUntil\":" + (vigenteHasta == null ? "null" : "\"" + vigenteHasta + "\"")
                + "}";
    }

    /** Crea el horario y devuelve su id, sacado del cuerpo de la respuesta. */
    private String crearHorario(String dia, String desde, String hasta) {
        ResponseEntity<String> respuesta = post(ruta(), cuerpo(dia, desde, hasta), ADMIN);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return respuesta.getBody().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void vaciarHorariosYGrupos(UUID club) {
        jdbc.update("DELETE FROM group_schedules WHERE group_id IN"
                + " (SELECT id FROM training_groups WHERE club_id = ?)", club);
        jdbc.update("DELETE FROM training_groups WHERE club_id = ?", club);
        jdbc.update("DELETE FROM seasons WHERE club_id = ?", club);
    }

    private void borrarTodo() {
        vaciarHorariosYGrupos(CLUB);
        vaciarHorariosYGrupos(CLUB_AJENO);
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

    private UUID crearGrupo(UUID club, String nombre) {
        UUID temporada = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO seasons"
                        + " (id, club_id, name, start_date, end_date, active, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, true, now(), now())",
                temporada, club, "Temporada " + nombre, INICIO, FIN);
        jdbc.update("INSERT INTO training_groups"
                        + " (id, club_id, season_id, name, category, level, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, 'ALEVIN', 'COMPETICION', now(), now())",
                id, club, temporada, nombre);
        return id;
    }

    private ResponseEntity<String> get(String ruta, String usuario) {
        return rest.exchange(ruta, HttpMethod.GET,
                new HttpEntity<>(autorizacion(usuario)), String.class);
    }

    private ResponseEntity<String> post(String ruta, String cuerpo, String usuario) {
        return conCuerpo(ruta, HttpMethod.POST, cuerpo, usuario);
    }

    private ResponseEntity<String> put(String ruta, String cuerpo, String usuario) {
        return conCuerpo(ruta, HttpMethod.PUT, cuerpo, usuario);
    }

    private ResponseEntity<String> delete(String ruta, String usuario) {
        return rest.exchange(ruta, HttpMethod.DELETE,
                new HttpEntity<>(autorizacion(usuario)), String.class);
    }

    private ResponseEntity<String> conCuerpo(String ruta, HttpMethod metodo,
                                             String cuerpo, String usuario) {
        HttpHeaders cabeceras = autorizacion(usuario);
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(ruta, metodo, new HttpEntity<>(cuerpo, cabeceras), String.class);
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
