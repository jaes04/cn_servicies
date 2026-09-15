package es.jaes.cn_servicies.document_delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bloque 3c: el informe de documentacion pendiente.
 *
 * <p>Montaje, todos en la temporada activa salvo donde se dice:
 * <ul>
 *   <li>Ana (Alevín A): certificado, licencia y documento en regla. <b>No sale.</b></li>
 *   <li>Bruno (Alevín A): nada. <b>Sale</b>, todo {@code MISSING}.</li>
 *   <li>Carla (Infantil B): certificado del curso pasado. <b>Sale</b>, {@code EXPIRED}.</li>
 *   <li>Diego: sin grupo. <b>No sale</b>, aunque no tenga nada.</li>
 *   <li>Elena (Alevín A, baja ayer): nada. <b>No sale</b>: hoy no esta en ningun grupo.</li>
 *   <li>Fran (Alevín A): todo en regla salvo el documento, que caduca en diez dias. <b>Sale.</b></li>
 *   <li>Gema (Alevín A): certificado y licencia en regla, y sin documento en la ficha.
 *       <b>No sale</b>: el documento no se le exige.</li>
 *   <li>Hugo (Alevín A e Infantil B): nada. <b>Sale una vez</b>, con los dos grupos.</li>
 * </ul>
 * El entrenador lleva Alevín A e Infantil B no tiene entrenador.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PendingDocumentationEndpointTest {

    private static final UUID CLUB = UUID.fromString("eeee1111-0000-0000-0000-000000001111");
    private static final UUID CLUB_SIN_TEMPORADA = UUID.fromString("eeee1111-0000-0000-0000-000000002222");

    private static final String CLAVE = "clave-de-prueba-it";
    private static final String ADMIN = "admin_pendientes_it";
    private static final String ENTRENADOR = "coach_pendientes_it";
    private static final String SIN_GRUPO = "singrupo_pendientes_it";
    private static final String SOCIO = "socio_pendientes_it";
    private static final String ADMIN_SIN_TEMPORADA = "admin_sintemporada_it";

    private static final String RUTA = "/api/reports/documents/pending";

    private static final LocalDate HOY = LocalDate.now();
    private static final LocalDate INICIO = HOY.minusMonths(3);
    private static final LocalDate FIN = HOY.plusMonths(8);

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private final Map<String, String> tokens = new HashMap<>();
    private final ObjectMapper json = new ObjectMapper();

    private UUID activa;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();

        crearClub(CLUB, "club-pendientes-it");
        crearClub(CLUB_SIN_TEMPORADA, "club-sin-temporada-it");
        crearUsuario(CLUB, ADMIN, "ROLE_ADMIN");
        crearUsuario(CLUB, ENTRENADOR, "ROLE_TECHNICAL_STAFF");
        crearUsuario(CLUB, SIN_GRUPO, "ROLE_TECHNICAL_STAFF");
        crearUsuario(CLUB, SOCIO, "ROLE_USER");
        crearUsuario(CLUB_SIN_TEMPORADA, ADMIN_SIN_TEMPORADA, "ROLE_ADMIN");

        activa = crearTemporada("Temporada actual", INICIO, FIN, true);
        UUID pasada = crearTemporada("Temporada pasada", INICIO.minusYears(1), INICIO.minusDays(1), false);

        UUID alevin = crearGrupo("Alevín A", true);
        UUID infantil = crearGrupo("Infantil B", false);

        UUID ana = crearAtleta("Ana", "80000001A");
        apuntar(ana, alevin, null);
        certificado(ana, activa);
        licencia(ana, activa);
        identidad(ana, HOY.plusYears(5));

        apuntar(crearAtleta("Bruno", "80000002B"), alevin, null);

        UUID carla = crearAtleta("Carla", "80000003C");
        apuntar(carla, infantil, null);
        certificado(carla, pasada);
        licencia(carla, activa);
        identidad(carla, HOY.plusYears(5));

        crearAtleta("Diego", "80000004D");

        apuntar(crearAtleta("Elena", "80000005E"), alevin, HOY.minusDays(1));

        UUID fran = crearAtleta("Fran", "80000006F");
        apuntar(fran, alevin, null);
        certificado(fran, activa);
        licencia(fran, activa);
        identidad(fran, HOY.plusDays(10));

        UUID gema = crearAtleta("Gema", "80000007G");
        jdbc.update("UPDATE athletes SET dni = NULL WHERE id = ?", gema);
        apuntar(gema, alevin, null);
        certificado(gema, activa);
        licencia(gema, activa);

        UUID hugo = crearAtleta("Hugo", "80000008H");
        apuntar(hugo, alevin, null);
        apuntar(hugo, infantil, null);
    }

    @AfterAll
    void fin() {
        modoPublico();
        borrarTodo();
    }

    // ----------------------------------------------------------------
    //  1. Quién sale
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el administrador ve a quien tiene algo pendiente, y a nadie más")
    void quienSale() throws Exception {
        JsonNode informe = informe(ADMIN);

        assertThat(nombres(informe)).containsExactly(
                "Bruno Documentos", "Carla Documentos", "Fran Documentos", "Hugo Documentos");
        assertThat(informe.get("total").asInt()).isEqualTo(4);
        assertThat(informe.get("seasonId").asText()).isEqualTo(activa.toString());
    }

    @Test
    @DisplayName("sin nada registrado, los tres papeles salen como MISSING")
    void sinNada() throws Exception {
        JsonNode bruno = atleta(informe(ADMIN), "Bruno Documentos");

        assertThat(bruno.get("medicalCertificate").asText()).isEqualTo("MISSING");
        assertThat(bruno.get("licenseApplication").asText()).isEqualTo("MISSING");
        assertThat(bruno.get("identityDocument").asText()).isEqualTo("MISSING");
    }

    @Test
    @DisplayName("el certificado del curso pasado sale como EXPIRED, con lo demás en regla")
    void certificadoDelCursoPasado() throws Exception {
        JsonNode carla = atleta(informe(ADMIN), "Carla Documentos");

        assertThat(carla.get("medicalCertificate").asText()).isEqualTo("EXPIRED");
        assertThat(carla.get("licenseApplication").asText()).isEqualTo("VALID");
    }

    /** Avisar cuando todavia vale es justo lo que el aviso tiene que hacer. */
    @Test
    @DisplayName("un documento a punto de caducar cuenta como pendiente")
    void aPuntoDeCaducarCuenta() throws Exception {
        assertThat(atleta(informe(ADMIN), "Fran Documentos").get("identityDocument").asText())
                .isEqualTo("EXPIRING_SOON");
    }

    @Test
    @DisplayName("quien está en dos grupos sale una vez, con los dos")
    void dosGrupos() throws Exception {
        List<String> grupos = new ArrayList<>();
        atleta(informe(ADMIN), "Hugo Documentos").get("groups").forEach(g -> grupos.add(g.asText()));

        assertThat(grupos).containsExactlyInAnyOrder("Alevín A", "Infantil B");
    }

    @Test
    @DisplayName("la respuesta no lleva documento de identidad ni fecha de nacimiento")
    void minimizacion() {
        String cuerpo = get(ADMIN).getBody();

        assertThat(cuerpo).doesNotContain("80000002B").doesNotContain("dni").doesNotContain("birthDate");
    }

    // ----------------------------------------------------------------
    //  2. Quién lo ve
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el entrenador ve solo los atletas de sus grupos, y solo sus grupos")
    void elEntrenadorVeLosSuyos() throws Exception {
        JsonNode informe = informe(ENTRENADOR);

        assertThat(nombres(informe)).containsExactly("Bruno Documentos", "Fran Documentos", "Hugo Documentos");
        List<String> gruposDeHugo = new ArrayList<>();
        atleta(informe, "Hugo Documentos").get("groups").forEach(g -> gruposDeHugo.add(g.asText()));
        assertThat(gruposDeHugo).containsExactly("Alevín A");
    }

    @Test
    @DisplayName("un entrenador sin grupos recibe el informe vacío")
    void entrenadorSinGrupos() throws Exception {
        assertThat(informe(SIN_GRUPO).get("total").asInt()).isZero();
    }

    @Test
    @DisplayName("un socio sin rol no lo ve")
    void elSocioNo() {
        assertThat(get(SOCIO).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("sin temporada activa, la temporada llega nula y la lista vacía")
    void sinTemporadaActiva() throws Exception {
        JsonNode informe = informe(ADMIN_SIN_TEMPORADA);

        assertThat(informe.get("seasonId").isNull()).isTrue();
        assertThat(informe.get("total").asInt()).isZero();
    }

    // ----------------------------------------------------------------
    //  Andamiaje
    // ----------------------------------------------------------------

    private JsonNode informe(String usuario) throws Exception {
        ResponseEntity<String> respuesta = get(usuario);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(respuesta.getBody());
    }

    private List<String> nombres(JsonNode informe) {
        List<String> nombres = new ArrayList<>();
        informe.get("athletes").forEach(a -> nombres.add(a.get("athleteName").asText()));
        return nombres;
    }

    private JsonNode atleta(JsonNode informe, String nombre) {
        for (JsonNode a : informe.get("athletes")) {
            if (a.get("athleteName").asText().equals(nombre)) {
                return a;
            }
        }
        throw new AssertionError("no aparece " + nombre + " en el informe");
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void borrarTodo() {
        for (UUID club : List.of(CLUB, CLUB_SIN_TEMPORADA)) {
            jdbc.update("DELETE FROM document_deliveries WHERE club_id = ?", club);
            jdbc.update("DELETE FROM medical_certificates WHERE club_id = ?", club);
            jdbc.update("DELETE FROM athlete_groups WHERE athlete_id IN"
                    + " (SELECT id FROM athletes WHERE club_id = ?)", club);
            jdbc.update("DELETE FROM training_groups WHERE club_id = ?", club);
            jdbc.update("DELETE FROM seasons WHERE club_id = ?", club);
            jdbc.update("DELETE FROM athletes WHERE club_id = ?", club);
            jdbc.update("DELETE FROM user_roles WHERE user_id IN"
                    + " (SELECT id FROM users WHERE club_id = ?)", club);
            jdbc.update("DELETE FROM users WHERE club_id = ?", club);
            jdbc.update("DELETE FROM clubs WHERE id = ?", club);
        }
    }

    private void crearClub(UUID id, String slug) {
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, ?, ?, true, now())", id, "Club " + slug, slug);
    }

    private void crearUsuario(UUID club, String usuario, String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users"
                        + " (id, club_id, username, email, password_hash, blocked, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, false, now(), now())",
                id, club, usuario, usuario + "@it.local", passwordEncoder.encode(CLAVE));
        jdbc.update("INSERT INTO user_roles (user_id, role_id)"
                + " SELECT ?, id FROM roles WHERE name = ?", id, rol);
    }

    private UUID crearTemporada(String nombre, LocalDate inicio, LocalDate fin, boolean esActiva) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO seasons"
                        + " (id, club_id, name, start_date, end_date, active, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, now(), now())",
                id, CLUB, nombre, inicio, fin, esActiva);
        return id;
    }

    private UUID crearGrupo(String nombre, boolean conEntrenador) {
        UUID id = UUID.randomUUID();
        if (conEntrenador) {
            jdbc.update("INSERT INTO training_groups"
                            + " (id, club_id, season_id, coach_id, name, category, level, created_at, updated_at)"
                            + " SELECT ?, ?, ?, u.id, ?, 'ALEVIN', 'COMPETICION', now(), now()"
                            + " FROM users u WHERE u.club_id = ? AND u.username = ?",
                    id, CLUB, activa, nombre, CLUB, ENTRENADOR);
        } else {
            jdbc.update("INSERT INTO training_groups"
                            + " (id, club_id, season_id, name, category, level, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, 'INFANTIL', 'COMPETICION', now(), now())",
                    id, CLUB, activa, nombre);
        }
        return id;
    }

    private UUID crearAtleta(String nombre, String dni) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO athletes"
                        + " (id, club_id, first_name, last_name, birth_date, dni, gender_id, created_at, updated_at)"
                        + " SELECT ?, ?, ?, 'Documentos', DATE '2012-05-05', ?, g.id, now(), now()"
                        + " FROM genders g WHERE g.name = 'FEMALE'",
                id, CLUB, nombre, dni);
        return id;
    }

    private void apuntar(UUID atleta, UUID grupo, LocalDate baja) {
        if (baja == null) {
            jdbc.update("INSERT INTO athlete_groups (id, athlete_id, group_id, joined_on, created_at)"
                    + " VALUES (?, ?, ?, ?, now())", UUID.randomUUID(), atleta, grupo, INICIO);
        } else {
            jdbc.update("INSERT INTO athlete_groups"
                            + " (id, athlete_id, group_id, joined_on, left_on, leave_reason, created_at)"
                            + " VALUES (?, ?, ?, ?, ?, 'OTHER', now())",
                    UUID.randomUUID(), atleta, grupo, INICIO, baja);
        }
    }

    private void certificado(UUID atleta, UUID temporada) {
        jdbc.update("INSERT INTO medical_certificates"
                        + " (id, club_id, athlete_id, season_id, issued_on, expires_on,"
                        + "  validated_by_id, validated_at, created_at)"
                        + " SELECT ?, ?, ?, s.id, s.start_date, s.end_date, u.id, now(), now()"
                        + " FROM seasons s JOIN users u ON u.club_id = s.club_id AND u.username = ?"
                        + " WHERE s.id = ?",
                UUID.randomUUID(), CLUB, atleta, ADMIN, temporada);
    }

    private void licencia(UUID atleta, UUID temporada) {
        jdbc.update("INSERT INTO document_deliveries"
                        + " (id, club_id, athlete_id, type, season_id, valid_from, valid_until,"
                        + "  delivered_on, registered_by_id, registered_at, created_at)"
                        + " SELECT ?, ?, ?, 'LICENSE_APPLICATION', ?, NULL, NULL, CURRENT_DATE, u.id, now(), now()"
                        + " FROM users u WHERE u.club_id = ? AND u.username = ?",
                UUID.randomUUID(), CLUB, atleta, temporada, CLUB, ADMIN);
    }

    private void identidad(UUID atleta, LocalDate caduca) {
        jdbc.update("INSERT INTO document_deliveries"
                        + " (id, club_id, athlete_id, type, season_id, valid_from, valid_until,"
                        + "  delivered_on, registered_by_id, registered_at, created_at)"
                        + " SELECT ?, ?, ?, 'IDENTITY_DOCUMENT', NULL, NULL, ?, CURRENT_DATE, u.id, now(), now()"
                        + " FROM users u WHERE u.club_id = ? AND u.username = ?",
                UUID.randomUUID(), CLUB, atleta, caduca, CLUB, ADMIN);
    }

    private ResponseEntity<String> get(String usuario) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(tokens.computeIfAbsent(usuario, this::iniciarSesion));
        return rest.exchange(RUTA, HttpMethod.GET, new HttpEntity<>(cabeceras), String.class);
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
