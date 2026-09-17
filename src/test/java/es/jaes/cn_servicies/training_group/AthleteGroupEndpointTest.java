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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 1.4: la API de composicion de grupos.
 *
 * <p>Lo que se comprueba por HTTP es lo que no se ve en el servicio: que el
 * lote se aplique <b>entero o nada</b>, que la consulta por fecha devuelva
 * quien estaba entonces, y que la baja no borre.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AthleteGroupEndpointTest {

    private static final UUID CLUB = UUID.fromString("66666666-0000-0000-0000-000000000066");
    private static final String SLUG = "club-composicion-it";
    private static final String CLAVE = "clave-de-prueba-it";
    private static final String ADMIN = "admin_comp_it";

    private static final LocalDate ALTA = LocalDate.of(2024, 9, 15);
    private static final LocalDate BAJA = LocalDate.of(2025, 1, 31);

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID temporada;
    private UUID grupo;
    private UUID uno;
    private UUID dos;
    private UUID tres;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Composición IT', ?, true, now())", CLUB, SLUG);
        crearAdmin();
    }

    @BeforeEach
    void datos() {
        modoPublico();
        vaciar();

        temporada = UUID.randomUUID();
        jdbc.update("INSERT INTO seasons"
                        + " (id, club_id, name, start_date, end_date, active, created_at, updated_at)"
                        + " VALUES (?, ?, '2024/2025', DATE '2024-09-01', DATE '2025-08-31',"
                        + "  true, now(), now())",
                temporada, CLUB);

        grupo = UUID.randomUUID();
        jdbc.update("INSERT INTO training_groups"
                        + " (id, club_id, season_id, name, category, level, max_slots, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'Alevín A', 'ALEVIN', 'COMPETICION', 12, now(), now())",
                grupo, CLUB, temporada);

        uno = crearAtleta("11111191A");
        dos = crearAtleta("11111192B");
        tres = crearAtleta("11111193C");
    }

    @AfterAll
    void fin() {
        modoPublico();
        borrarTodo();
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void vaciar() {
        jdbc.update("DELETE FROM athlete_groups WHERE athlete_id IN"
                + " (SELECT id FROM athletes WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM training_groups WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM athletes WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM seasons WHERE club_id = ?", CLUB);
    }

    private void borrarTodo() {
        vaciar();
        jdbc.update("DELETE FROM user_roles WHERE user_id IN"
                + " (SELECT id FROM users WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM users WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM clubs WHERE id = ?", CLUB);
    }

    private void crearAdmin() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users"
                        + " (id, club_id, username, email, password_hash, blocked, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, false, now(), now())",
                id, CLUB, ADMIN, ADMIN + "@it.local", passwordEncoder.encode(CLAVE));
        jdbc.update("INSERT INTO user_roles (user_id, role_id)"
                + " SELECT ?, id FROM roles WHERE name = 'ROLE_ADMIN'", id);
    }

    private UUID crearAtleta(String dni) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO athletes"
                        + " (id, club_id, first_name, last_name, birth_date, dni, gender_id, created_at, updated_at)"
                        + " SELECT ?, ?, 'Nadador', ?, DATE '2013-01-01', ?, g.id, now(), now()"
                        + " FROM genders g WHERE g.name = 'MALE'",
                id, CLUB, dni.substring(7), dni);
        return id;
    }

    private ResponseEntity<String> asignar(String idsJson, LocalDate fecha) {
        return post("/api/groups/" + grupo + "/athletes",
                "{\"athleteIds\":" + idsJson + ",\"joinedOn\":\"" + fecha + "\"}");
    }

    // ----------------------------------------------------------------

    @Test
    @DisplayName("el lote se aplica entero: si uno ya está, no entra ninguno")
    void elLoteEsAtomico() {
        assertThat(asignar("[\"" + uno + "\"]", ALTA).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        // El segundo lote lleva a uno que ya está y a otro que no.
        ResponseEntity<String> segundo = asignar("[\"" + dos + "\",\"" + uno + "\"]", ALTA);

        assertThat(segundo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/api/groups/" + grupo + "/athletes?date=" + ALTA).getBody())
                .as("media asignación deja al club sin saber quién entró")
                .doesNotContain(dos.toString());
    }

    @Test
    @DisplayName("dar de alta a varios de una vez funciona")
    void elLoteCompleto() {
        ResponseEntity<String> respuesta =
                asignar("[\"" + uno + "\",\"" + dos + "\",\"" + tres + "\"]", ALTA);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(get("/api/groups/" + grupo + "/athletes?date=" + ALTA).getBody())
                .contains(uno.toString()).contains(dos.toString()).contains(tres.toString());
    }

    @Test
    @DisplayName("el listado de grupos trae el conteo de miembros")
    void elListadoTraeElConteo() {
        asignar("[\"" + uno + "\",\"" + dos + "\"]", ALTA);

        assertThat(get("/api/groups?seasonId=" + temporada).getBody())
                .contains("\"memberCount\":2")
                .contains("\"maxSlots\":12");
    }

    @Test
    @DisplayName("la consulta por fecha devuelve quién estaba ese día, no quién está hoy")
    void miembrosAUnaFecha() {
        asignar("[\"" + uno + "\"]", ALTA);
        baja(uno, BAJA);

        assertThat(get("/api/groups/" + grupo + "/athletes?date=" + BAJA).getBody())
                .as("el día de la baja todavía era miembro")
                .contains(uno.toString());
        assertThat(get("/api/groups/" + grupo + "/athletes?date=" + BAJA.plusDays(1)).getBody())
                .doesNotContain(uno.toString());
    }

    @Test
    @DisplayName("la baja saca al atleta del grupo pero deja la pertenencia en su historial")
    void laBajaNoBorra() {
        asignar("[\"" + uno + "\"]", ALTA);

        assertThat(baja(uno, BAJA).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(get("/api/groups/" + grupo + "/athletes").getBody())
                .as("hoy ya no está en el grupo")
                .doesNotContain(uno.toString());
        assertThat(get("/api/athletes/" + uno + "/group-history").getBody())
                .as("pero consta que estuvo, con su fecha y su motivo")
                .contains("Alevín A")
                .contains("END_OF_SEASON")
                .contains(BAJA.toString());
    }

    @Test
    @DisplayName("el historial se puede acotar a una temporada")
    void historialPorTemporada() {
        asignar("[\"" + uno + "\"]", ALTA);

        assertThat(get("/api/athletes/" + uno + "/group-history?seasonId=" + temporada).getBody())
                .contains("Alevín A");
        assertThat(get("/api/athletes/" + uno + "/group-history?seasonId=" + UUID.randomUUID()).getBody())
                .isEqualTo("[]");
    }

    // ----------------------------------------------------------------

    private ResponseEntity<String> baja(UUID atleta, LocalDate fecha) {
        return rest.exchange(
                "/api/groups/" + grupo + "/athletes/" + atleta
                        + "?reason=END_OF_SEASON&leftOn=" + fecha,
                HttpMethod.DELETE, new HttpEntity<>(autorizacion()), String.class);
    }

    private ResponseEntity<String> get(String ruta) {
        return rest.exchange(ruta, HttpMethod.GET, new HttpEntity<>(autorizacion()), String.class);
    }

    private ResponseEntity<String> post(String ruta, String cuerpo) {
        HttpHeaders cabeceras = autorizacion();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(ruta, HttpMethod.POST,
                new HttpEntity<>(cuerpo, cabeceras), String.class);
    }

    private HttpHeaders autorizacion() {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(iniciarSesion());
        return cabeceras;
    }

    private String iniciarSesion() {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        String cuerpo = "{\"clubSlug\":\"" + SLUG + "\",\"username\":\"" + ADMIN + "\",\"password\":\"" + CLAVE + "\"}";

        ResponseEntity<String> respuesta = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(cuerpo, cabeceras), String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta.getBody().replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}
