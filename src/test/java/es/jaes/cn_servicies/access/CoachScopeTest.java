package es.jaes.cn_servicies.access;

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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea S.3.3.b: el entrenador, acotado a los grupos que lleva.
 *
 * <p>Hasta esta tarea, cualquier entrenador del club llegaba a cualquier grupo,
 * a cualquier roster y a la ficha de cualquier atleta. El filtro de tenant y RLS
 * no tenian nada que decir: todo era de su club. Lo que se prueba aqui es la
 * pregunta que ninguna de las dos capas sabe responder, <b>"¿es de tus
 * grupos?"</b>.
 *
 * <p>Montaje: tres grupos en la temporada activa.
 * <ul>
 *   <li>Alevin A — principal {@code PRINCIPAL}, ayudante {@code AYUDANTE}.
 *       Ana esta hoy; Carla lo dejo ayer.</li>
 *   <li>Infantil B — principal {@code OTRO}. Bruno.</li>
 *   <li>Sin entrenador — nadie lo lleva.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoachScopeTest {

    private static final UUID CLUB = UUID.fromString("aaaa6666-0000-0000-0000-000000006611");
    private static final String SLUG = "club-entrenador-acotado-it";

    private static final String CLAVE = "clave-de-prueba-it";
    private static final String ADMIN = "admin_scope_it";
    private static final String PRINCIPAL = "principal_scope_it";
    private static final String AYUDANTE = "ayudante_scope_it";
    private static final String OTRO = "otro_scope_it";
    private static final String SIN_GRUPO = "singrupo_scope_it";
    private static final String SOCIO = "socio_scope_it";

    private static final LocalDate HOY = LocalDate.now();
    private static final LocalDate INICIO = HOY.minusMonths(3);
    private static final LocalDate FIN = HOY.plusMonths(8);
    private static final LocalDate AYER = HOY.minusDays(1);
    /** Antes de la baja de Carla: en esa sesion todavia estaba en el grupo. */
    private static final LocalDate HACE_UNA_SEMANA = HOY.minusDays(7);

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private final Map<String, String> tokens = new HashMap<>();
    private final Map<String, UUID> usuarios = new HashMap<>();

    private UUID temporada;
    private UUID alevin;
    private UUID infantil;
    private UUID sinEntrenador;
    private UUID ana;
    private UUID bruno;
    private UUID carla;
    private UUID sesionAlevin;
    private UUID sesionInfantil;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();

        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, ?, ?, true, now())", CLUB, "Club " + SLUG, SLUG);

        crearUsuario(ADMIN, "ROLE_ADMIN");
        crearUsuario(PRINCIPAL, "ROLE_TECHNICAL_STAFF");
        crearUsuario(AYUDANTE, "ROLE_TECHNICAL_STAFF");
        crearUsuario(OTRO, "ROLE_TECHNICAL_STAFF");
        crearUsuario(SIN_GRUPO, "ROLE_TECHNICAL_STAFF");
        crearUsuario(SOCIO, "ROLE_USER");

        temporada = UUID.randomUUID();
        jdbc.update("INSERT INTO seasons"
                        + " (id, club_id, name, start_date, end_date, active, created_at, updated_at)"
                        + " VALUES (?, ?, 'Temporada acotado', ?, ?, true, now(), now())",
                temporada, CLUB, INICIO, FIN);

        alevin = crearGrupo("Alevín A", usuarios.get(PRINCIPAL));
        infantil = crearGrupo("Infantil B", usuarios.get(OTRO));
        sinEntrenador = crearGrupo("Sin entrenador", null);
        jdbc.update("INSERT INTO group_assistant_coaches (group_id, user_id) VALUES (?, ?)",
                alevin, usuarios.get(AYUDANTE));

        ana = crearAtleta("Ana", "40000001A");
        bruno = crearAtleta("Bruno", "40000002B");
        carla = crearAtleta("Carla", "40000003C");

        apuntar(ana, alevin, null);
        apuntar(bruno, infantil, null);
        apuntar(carla, alevin, AYER);

        sesionAlevin = crearSesion(alevin, HACE_UNA_SEMANA);
        sesionInfantil = crearSesion(infantil, HACE_UNA_SEMANA);
    }

    @AfterAll
    void fin() {
        modoPublico();
        borrarTodo();
    }

    // ----------------------------------------------------------------
    //  1. Grupos
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el listado de grupos del entrenador trae solo los suyos")
    void listadoDeGruposAcotado() {
        String cuerpo = get("/api/groups?seasonId=" + temporada, PRINCIPAL).getBody();

        assertThat(cuerpo).contains("Alevín A");
        assertThat(cuerpo).doesNotContain("Infantil B").doesNotContain("Sin entrenador");
    }

    @Test
    @DisplayName("el administrador sigue viendo todos los grupos")
    void elAdminVeTodos() {
        assertThat(get("/api/groups?seasonId=" + temporada, ADMIN).getBody())
                .contains("Alevín A").contains("Infantil B").contains("Sin entrenador");
    }

    @Test
    @DisplayName("el ayudante entra en el grupo igual que el principal")
    void elAyudanteEntra() {
        assertThat(get("/api/groups/" + alevin, AYUDANTE).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/groups?seasonId=" + temporada, AYUDANTE).getBody()).contains("Alevín A");
    }

    @Test
    @DisplayName("otro entrenador no entra en el grupo ni en nada que cuelgue de él")
    void otroEntrenadorNoEntra() {
        assertThat(get("/api/groups/" + alevin, OTRO).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/groups/" + alevin + "/athletes", OTRO).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/groups/" + alevin + "/schedules", OTRO).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/groups/" + alevin + "/sessions?from=" + INICIO + "&to=" + FIN, OTRO)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("un grupo sin entrenador solo lo ve el administrador")
    void grupoSinEntrenador() {
        assertThat(get("/api/groups/" + sinEntrenador, SIN_GRUPO).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/groups/" + sinEntrenador, ADMIN).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ----------------------------------------------------------------
    //  2. Sesiones y pasar lista
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el ayudante pasa lista en su grupo")
    void elAyudantePasaLista() {
        assertThat(get(roster(sesionAlevin), AYUDANTE).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(put("/api/sessions/" + sesionAlevin + "/attendance", lista(ana), AYUDANTE)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * El caso que motivo la tarea: hasta ahora, cualquier entrenador del club
     * escribia la asistencia de menores de un grupo que no llevaba.
     */
    @Test
    @DisplayName("otro entrenador no ve el roster, no pasa lista y no cancela")
    void otroEntrenadorNoPasaLista() {
        assertThat(get(roster(sesionAlevin), OTRO).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(put("/api/sessions/" + sesionAlevin + "/attendance", lista(ana), OTRO)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/api/sessions/" + sesionAlevin + "/cancellation?reason=OTHER", "", OTRO)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/sessions/" + sesionAlevin, OTRO).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * La ficha de Carla ya no la ve (se fue ayer), pero el roster de la sesion de
     * hace una semana si la trae: a esa se llega por el grupo, y ese dia estaba.
     * Es exactamente la diferencia entre "ficha vigente" y "historico del grupo".
     */
    @Test
    @DisplayName("quien dejó el grupo sigue en el roster de las sesiones en que estaba")
    void elRosterPasadoConservaAQuienSeFue() {
        assertThat(get(roster(sesionAlevin), PRINCIPAL).getBody()).contains("Carla");
    }

    // ----------------------------------------------------------------
    //  3. Informes
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el informe y el CSV de un grupo ajeno son un 404; el del suyo sale")
    void informesAcotados() {
        String rango = "?from=" + INICIO + "&to=" + FIN;

        assertThat(get("/api/reports/attendance/group/" + alevin + rango, OTRO).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/reports/attendance/group/" + alevin + "/export" + rango, OTRO)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/reports/attendance/group/" + alevin + "/gaps" + rango, OTRO)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(get("/api/reports/attendance/group/" + alevin + "/export" + rango, PRINCIPAL)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("el aviso de pendientes del entrenador trae sus grupos, no los del club")
    void pendientesAcotados() {
        String cuerpo = get("/api/reports/attendance/pending", OTRO).getBody();

        assertThat(cuerpo).contains(sesionInfantil.toString());
        assertThat(cuerpo).doesNotContain(sesionAlevin.toString());
    }

    // ----------------------------------------------------------------
    //  4. Atletas
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el entrenador abre la ficha de quien hoy está en su grupo, y de nadie más")
    void fichasVigentes() {
        assertThat(get("/api/athletes/" + ana, PRINCIPAL).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/athletes/" + bruno, PRINCIPAL).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("quien dejó su grupo ayer ya no es visible en su ficha")
    void quienSeFueNoSeVe() {
        assertThat(get("/api/athletes/" + carla, PRINCIPAL).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("el listado de atletas del entrenador trae solo los de hoy en sus grupos")
    void listadoDeAtletasAcotado() {
        String cuerpo = get("/api/athletes?size=100", PRINCIPAL).getBody();

        assertThat(cuerpo).contains("Ana");
        assertThat(cuerpo).doesNotContain("Bruno").doesNotContain("Carla");
    }

    @Test
    @DisplayName("el estado del certificado y de los consentimientos de un atleta ajeno es un 404")
    void estadosAcotados() {
        assertThat(get("/api/medical-certificates/athlete/" + ana + "/status", OTRO).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/consents/athlete/" + ana + "/status", OTRO).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * La clave de invitacion da a una cuenta acceso a los datos de un menor. El
     * entrenador la mantiene —decision del club—, pero solo para los suyos.
     */
    @Test
    @DisplayName("el entrenador genera claves de invitación solo para atletas de sus grupos")
    void clavesAcotadas() {
        assertThat(post("/api/athlete-links/" + ana + "/key", "{\"type\":\"TUTOR\"}", OTRO)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/api/athlete-links/" + ana + "/key", "{\"type\":\"TUTOR\"}", PRINCIPAL)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ----------------------------------------------------------------
    //  5. Asignar entrenadores
    // ----------------------------------------------------------------

    /**
     * Antes daba igual a quien se asignara: la columna no daba permisos. Ahora da
     * acceso a menores, y asignar a un socio es un agujero, no un error de datos.
     */
    @Test
    @DisplayName("no se puede asignar como entrenador a un socio sin rol técnico")
    void entrenadorSinRol() {
        ResponseEntity<String> respuesta = put("/api/groups/" + sinEntrenador,
                grupo("Sin entrenador", usuarios.get(SOCIO), null), ADMIN);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).contains("personal técnico");
    }

    @Test
    @DisplayName("el principal no puede figurar también como ayudante")
    void principalYAyudanteALaVez() {
        UUID principal = usuarios.get(PRINCIPAL);
        ResponseEntity<String> respuesta = post("/api/groups",
                grupo("Grupo nuevo", principal, principal), ADMIN);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).contains("ayudante");
    }

    // ----------------------------------------------------------------
    //  Andamiaje
    // ----------------------------------------------------------------

    private String roster(UUID sesion) {
        return "/api/sessions/" + sesion + "/roster";
    }

    private String lista(UUID atleta) {
        return "{\"entries\":[{\"athleteId\":\"" + atleta + "\",\"status\":\"PRESENT\"}]}";
    }

    private String grupo(String nombre, UUID principal, UUID ayudante) {
        return "{\"seasonId\":\"" + temporada + "\",\"name\":\"" + nombre + "\""
                + ",\"category\":\"ALEVIN\",\"level\":\"COMPETICION\""
                + (principal != null ? ",\"coachId\":\"" + principal + "\"" : "")
                + (ayudante != null ? ",\"assistantCoachIds\":[\"" + ayudante + "\"]" : "")
                + "}";
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void borrarTodo() {
        jdbc.update("DELETE FROM athlete_invite_keys WHERE athlete_id IN"
                + " (SELECT id FROM athletes WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM attendance WHERE session_id IN"
                + " (SELECT id FROM training_sessions WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM training_sessions WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM athlete_groups WHERE athlete_id IN"
                + " (SELECT id FROM athletes WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM group_assistant_coaches WHERE group_id IN"
                + " (SELECT id FROM training_groups WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM group_schedules WHERE group_id IN"
                + " (SELECT id FROM training_groups WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM training_groups WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM athletes WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM seasons WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN"
                + " (SELECT id FROM users WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM users WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM clubs WHERE id = ?", CLUB);
    }

    private void crearUsuario(String usuario, String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users"
                        + " (id, club_id, username, email, password_hash, blocked, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, false, now(), now())",
                id, CLUB, usuario, usuario + "@it.local", passwordEncoder.encode(CLAVE));
        jdbc.update("INSERT INTO user_roles (user_id, role_id)"
                + " SELECT ?, id FROM roles WHERE name = ?", id, rol);
        usuarios.put(usuario, id);
    }

    private UUID crearGrupo(String nombre, UUID entrenador) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO training_groups"
                        + " (id, club_id, season_id, coach_id, name, category, level, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, 'ALEVIN', 'COMPETICION', now(), now())",
                id, CLUB, temporada, entrenador, nombre);
        return id;
    }

    private UUID crearAtleta(String nombre, String dni) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO athletes"
                        + " (id, club_id, first_name, last_name, birth_date, dni, gender_id,"
                        + "  created_at, updated_at)"
                        + " SELECT ?, ?, ?, 'Nadadora', ?, ?, g.id, now(), now()"
                        + " FROM genders g WHERE g.name = 'FEMALE'",
                id, CLUB, nombre, LocalDate.of(2013, 4, 17), dni);
        return id;
    }

    private void apuntar(UUID atleta, UUID grupo, LocalDate baja) {
        jdbc.update("INSERT INTO athlete_groups"
                        + " (id, athlete_id, group_id, joined_on, left_on, leave_reason, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, now())",
                UUID.randomUUID(), atleta, grupo, INICIO, baja, baja != null ? "OTHER" : null);
    }

    private UUID crearSesion(UUID grupo, LocalDate fecha) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO training_sessions"
                        + " (id, club_id, group_id, schedule_id, session_date, start_time, end_time,"
                        + "  modality, status, created_at, updated_at)"
                        + " VALUES (?, ?, ?, NULL, ?, '18:00', '19:00', 'SWIMMING', 'SCHEDULED',"
                        + "  now(), now())",
                id, CLUB, grupo, fecha);
        return id;
    }

    private ResponseEntity<String> get(String ruta, String usuario) {
        return rest.exchange(ruta, HttpMethod.GET, new HttpEntity<>(autorizacion(usuario)), String.class);
    }

    private ResponseEntity<String> post(String ruta, String cuerpo, String usuario) {
        return rest.exchange(ruta, HttpMethod.POST, new HttpEntity<>(cuerpo, json(usuario)), String.class);
    }

    private ResponseEntity<String> put(String ruta, String cuerpo, String usuario) {
        return rest.exchange(ruta, HttpMethod.PUT, new HttpEntity<>(cuerpo, json(usuario)), String.class);
    }

    private HttpHeaders json(String usuario) {
        HttpHeaders cabeceras = autorizacion(usuario);
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return cabeceras;
    }

    private HttpHeaders autorizacion(String usuario) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(tokens.computeIfAbsent(usuario, this::iniciarSesion));
        return cabeceras;
    }

    private String iniciarSesion(String usuario) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        String cuerpo = "{\"clubSlug\":\"" + SLUG + "\",\"username\":\"" + usuario + "\",\"password\":\"" + CLAVE + "\"}";

        ResponseEntity<String> respuesta = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(cuerpo, cabeceras), String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta.getBody().replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}
