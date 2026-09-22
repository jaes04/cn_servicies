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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El listado de tutores, {@code GET /api/guardians}.
 *
 * <p>Lo que se protege es el alcance: el administrador ve todo su club y nada
 * del ajeno; el entrenador solo a los tutores de los atletas de sus grupos, y de
 * cada uno solo esos atletas —no el hermano que nada en otro grupo—.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GuardianEndpointTest {

    private static final UUID CLUB = UUID.fromString("eeeeeeee-0000-0000-0000-0000000000a1");
    private static final UUID OTRO_CLUB = UUID.fromString("eeeeeeee-0000-0000-0000-0000000000a2");
    private static final String SLUG = "club-guardians-it";
    private static final String CLAVE = "clave-de-prueba-it";

    private static final String ADMIN = "admin_guardian_it";
    private static final String ENTRENADOR = "staff_guardian_it";
    private static final String SOCIO = "user_guardian_it";

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID nadadora;
    private UUID fuera;
    private UUID prima;
    private UUID lejano;
    private UUID corregible;
    private UUID ajeno;

    // ----------------------------------------------------------------
    //  Datos de prueba
    // ----------------------------------------------------------------

    /**
     * <ul>
     *   <li>Tutora "Madre": de "Nadadora", que esta en el grupo del entrenador, y
     *       de "Hermano", que no.</li>
     *   <li>Tutor "Lejano": solo de "Fuera", que no esta en el grupo.</li>
     *   <li>Tutora "Borrada": borrado logico.</li>
     *   <li>Tutor "Ajeno": del otro club.</li>
     * </ul>
     */
    @BeforeAll
    void crearDatos() {
        modoPublico();
        borrarDatos();

        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Guardians IT', ?, true, now())", CLUB, SLUG);
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Otro Club Guardians IT', 'otro-club-guardians-it', true, now())", OTRO_CLUB);
        crearUsuario(ADMIN, "ROLE_ADMIN");
        crearUsuario(ENTRENADOR, "ROLE_TECHNICAL_STAFF");
        crearUsuario(SOCIO, "ROLE_USER");

        nadadora = crearAtleta(CLUB, "Nadadora");
        UUID hermano = crearAtleta(CLUB, "Hermano");
        fuera = crearAtleta(CLUB, "Fuera");
        // Solo para el test de reutilizar por documento, que le vincula a "Madre".
        prima = crearAtleta(CLUB, "Prima");

        UUID madre = crearTutor(CLUB, "Madre", "66666661A", null);
        vincular(nadadora, madre);
        vincular(hermano, madre);
        lejano = crearTutor(CLUB, "Lejano", "66666662B", null);
        vincular(fuera, lejano);
        vincular(nadadora, crearTutor(CLUB, "Borrada", "66666663C", "now()"));
        // Solo para los tests de correccion, que le cambian el nombre.
        corregible = crearTutor(CLUB, "Corregible", "66666665E", null);
        vincular(nadadora, corregible);

        ajeno = crearTutor(OTRO_CLUB, "Ajeno", "66666664D", null);
        vincular(crearAtleta(OTRO_CLUB, "Extranjera"), ajeno);

        ponerEnUnGrupoDelEntrenador(nadadora);
    }

    @AfterAll
    void fin() {
        modoPublico();
        borrarDatos();
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void borrarDatos() {
        for (UUID club : new UUID[]{CLUB, OTRO_CLUB}) {
            jdbc.update("DELETE FROM athlete_guardians WHERE guardian_id IN"
                    + " (SELECT id FROM guardians WHERE club_id = ?)", club);
            jdbc.update("DELETE FROM guardians WHERE club_id = ?", club);
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

    private void crearUsuario(String username, String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users"
                        + " (id, club_id, username, email, password_hash, blocked, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, false, now(), now())",
                id, CLUB, username, username + "@it.local", passwordEncoder.encode(CLAVE));
        jdbc.update("INSERT INTO user_roles (user_id, role_id)"
                + " SELECT ?, id FROM roles WHERE name = ?", id, rol);
    }

    private UUID crearAtleta(UUID club, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO athletes"
                        + " (id, club_id, first_name, last_name, birth_date, gender_id, created_at, updated_at)"
                        + " SELECT ?, ?, ?, 'Guardians', DATE '2015-05-05', g.id, now(), now()"
                        + " FROM genders g WHERE g.name = 'FEMALE'",
                id, club, nombre);
        return id;
    }

    private UUID crearTutor(UUID club, String nombre, String dni, String borradoEn) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO guardians"
                        + " (id, club_id, first_name, last_name, dni, email, created_at, updated_at, deleted_at)"
                        + " VALUES (?, ?, ?, 'Guardians', ?, ?, now(), now(), "
                        + (borradoEn == null ? "NULL" : borradoEn) + ")",
                id, club, nombre, dni, nombre.toLowerCase() + "@it.local");
        return id;
    }

    private void vincular(UUID atletaId, UUID tutorId) {
        jdbc.update("INSERT INTO athlete_guardians"
                        + " (id, athlete_id, guardian_id, relationship, created_at)"
                        + " VALUES (?, ?, ?, 'MOTHER', now())",
                UUID.randomUUID(), atletaId, tutorId);
    }

    private void ponerEnUnGrupoDelEntrenador(UUID atletaId) {
        UUID temporada = UUID.randomUUID();
        UUID grupo = UUID.randomUUID();
        jdbc.update("INSERT INTO seasons"
                        + " (id, club_id, name, start_date, end_date, active, created_at, updated_at)"
                        + " VALUES (?, ?, 'Temporada guardians', CURRENT_DATE - 90, CURRENT_DATE + 240,"
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

    // ----------------------------------------------------------------
    //  1. Administrador
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el administrador ve a todos los tutores de su club, con todos sus atletas")
    void elAdminVeTodoSuClub() {
        ResponseEntity<String> respuesta = get("/api/guardians", ADMIN);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody())
                .contains("Madre").contains("Lejano")
                .contains("Nadadora Guardians").contains("Hermano Guardians").contains("Fuera Guardians")
                .contains("\"hasAccount\":false");
    }

    @Test
    @DisplayName("el administrador no ve a los tutores de otro club")
    void elAdminNoVeOtroClub() {
        assertThat(get("/api/guardians", ADMIN).getBody())
                .doesNotContain("Ajeno").doesNotContain("Extranjera");
    }

    @Test
    @DisplayName("un tutor borrado no sale en el listado")
    void elBorradoNoSale() {
        assertThat(get("/api/guardians", ADMIN).getBody()).doesNotContain("Borrada");
    }

    @Test
    @DisplayName("q busca por nombre, documento o email")
    void buscaPorQ() {
        String porDni = get("/api/guardians?q=66666662b", ADMIN).getBody();
        assertThat(porDni).contains("Lejano").doesNotContain("Madre");

        String porEmail = get("/api/guardians?q=madre@it", ADMIN).getBody();
        assertThat(porEmail).contains("Madre").doesNotContain("Lejano");
    }

    // ----------------------------------------------------------------
    //  2. Entrenador
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el entrenador solo ve a los tutores de los atletas de sus grupos")
    void elEntrenadorSoloVeLosSuyos() {
        ResponseEntity<String> respuesta = get("/api/guardians", ENTRENADOR);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("Madre").contains("Nadadora Guardians");
        assertThat(respuesta.getBody())
                .as("el tutor de un atleta que no esta en sus grupos no sale")
                .doesNotContain("Lejano").doesNotContain("Fuera Guardians");
    }

    @Test
    @DisplayName("el entrenador no ve al hermano que nada en otro grupo")
    void elEntrenadorNoVeAlHermano() {
        assertThat(get("/api/guardians", ENTRENADOR).getBody()).doesNotContain("Hermano Guardians");
    }

    // ----------------------------------------------------------------
    //  3. Sin permiso
    // ----------------------------------------------------------------

    @Test
    @DisplayName("un socio sin rol recibe 403")
    void elSocioNoEntra() {
        assertThat(get("/api/guardians", SOCIO).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("sin token no se entra")
    void sinTokenNoSeEntra() {
        assertThat(rest.getForEntity("/api/guardians", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ----------------------------------------------------------------
    //  4. Alta de un tutor para un atleta que ya existe
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el administrador da de alta un tutor nuevo y queda vinculado; repetir no lo duplica")
    void elAdminDaDeAltaUnTutor() {
        String cuerpo = alta("Padre", "66666671A", "padre@it.local", "FATHER");

        ResponseEntity<String> respuesta = post("/api/athletes/" + nadadora + "/guardians", cuerpo, ADMIN);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuesta.getBody()).contains("Padre").contains("Nadadora Guardians").contains("FATHER");

        post("/api/athletes/" + nadadora + "/guardians", cuerpo, ADMIN);
        assertThat(tutoresConDocumento("66666671A")).isEqualTo(1);
        assertThat(vinculos(nadadora, "66666671A"))
                .as("repetir el alta no duplica el vinculo")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("si ya hay un tutor con ese documento se reutiliza, sin tocar su contacto")
    void reutilizaPorDocumento() {
        // Mismo documento que "Madre", en minusculas y con espacios, y otro email.
        String cuerpo = alta("Otra", " 66666661a ", "otro-email@it.local", "MOTHER");

        ResponseEntity<String> respuesta = post("/api/athletes/" + prima + "/guardians", cuerpo, ADMIN);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuesta.getBody()).contains("Madre").contains("madre@it.local")
                .contains("Prima Guardians").doesNotContain("otro-email");
        assertThat(tutoresConDocumento("66666661A")).isEqualTo(1);
    }

    @Test
    @DisplayName("el entrenador da de alta tutores en sus atletas, y en los demás recibe 404")
    void elEntrenadorSoloEnSusAtletas() {
        assertThat(post("/api/athletes/" + nadadora + "/guardians",
                alta("Abuela", "66666672B", "abuela@it.local", "LEGAL_GUARDIAN"), ENTRENADOR).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(post("/api/athletes/" + fuera + "/guardians",
                alta("Intruso", "66666673C", "intruso@it.local", "OTHER"), ENTRENADOR).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(tutoresConDocumento("66666673C"))
                .as("un alta denegada no deja el tutor creado")
                .isZero();
    }

    @Test
    @DisplayName("un socio no da de alta tutores, y sin parentesco es 400")
    void altaSinPermisoOSinParentesco() {
        assertThat(post("/api/athletes/" + nadadora + "/guardians",
                alta("Socio", "66666674D", "socio@it.local", "OTHER"), SOCIO).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        String sinParentesco = "{\"guardian\":{\"firstName\":\"Sin\",\"lastName\":\"Parentesco\","
                + "\"dni\":\"66666675E\",\"email\":\"sin@it.local\"}}";
        assertThat(post("/api/athletes/" + nadadora + "/guardians", sinParentesco, ADMIN).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ----------------------------------------------------------------
    //  5. Corregir la ficha de un tutor
    // ----------------------------------------------------------------

    @Test
    @DisplayName("se corrigen nombre y contacto, y el documento no cambia")
    void seCorrigeLaFicha() {
        ResponseEntity<String> respuesta = put("/api/guardians/" + corregible,
                correccion("Corregida", "corregida@it.local"), ADMIN);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("Corregida").contains("corregida@it.local")
                .contains("66666665E").contains("600000000");

        assertThat(put("/api/guardians/" + corregible,
                correccion("Corregida", "corregida@it.local"), ENTRENADOR).getStatusCode())
                .as("es tutor de una atleta de su grupo")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("el entrenador no corrige a un tutor ajeno a sus atletas, ni nadie a uno de otro club")
    void noSeCorrigeLoAjeno() {
        assertThat(put("/api/guardians/" + lejano, correccion("Cambiado", "x@it.local"), ENTRENADOR)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(put("/api/guardians/" + ajeno, correccion("Cambiado", "x@it.local"), ADMIN)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(put("/api/guardians/" + lejano, correccion("Cambiado", "x@it.local"), SOCIO)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(get("/api/guardians", ADMIN).getBody()).doesNotContain("Cambiado");
    }

    private String alta(String nombre, String dni, String email, String parentesco) {
        return "{\"guardian\":{\"firstName\":\"" + nombre + "\",\"lastName\":\"Guardians\",\"dni\":\"" + dni
                + "\",\"email\":\"" + email + "\"},\"relationship\":\"" + parentesco + "\"}";
    }

    private String correccion(String nombre, String email) {
        return "{\"firstName\":\"" + nombre + "\",\"lastName\":\"Guardians\",\"email\":\"" + email
                + "\",\"phone\":\"600000000\"}";
    }

    private int tutoresConDocumento(String dni) {
        modoPublico();
        return jdbc.queryForObject("SELECT count(*) FROM guardians WHERE club_id = ? AND dni = ?",
                Integer.class, CLUB, dni);
    }

    private int vinculos(UUID atletaId, String dni) {
        modoPublico();
        return jdbc.queryForObject("SELECT count(*) FROM athlete_guardians ag"
                        + " JOIN guardians g ON g.id = ag.guardian_id"
                        + " WHERE ag.athlete_id = ? AND g.dni = ?",
                Integer.class, atletaId, dni);
    }

    // ----------------------------------------------------------------
    //  Utilidades
    // ----------------------------------------------------------------

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

    private ResponseEntity<String> get(String ruta, String username) {
        return enviar(HttpMethod.GET, ruta, null, username);
    }

    private ResponseEntity<String> post(String ruta, String cuerpo, String username) {
        return enviar(HttpMethod.POST, ruta, cuerpo, username);
    }

    private ResponseEntity<String> put(String ruta, String cuerpo, String username) {
        return enviar(HttpMethod.PUT, ruta, cuerpo, username);
    }

    private ResponseEntity<String> enviar(HttpMethod metodo, String ruta, String cuerpo, String username) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(iniciarSesion(username));
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(ruta, metodo, new HttpEntity<>(cuerpo, cabeceras), String.class);
    }
}
