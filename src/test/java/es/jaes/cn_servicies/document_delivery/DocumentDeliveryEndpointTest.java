package es.jaes.cn_servicies.document_delivery;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bloque 3a: el registro de papeles entregados, y la subida de archivos apagada.
 *
 * <p>Tres cosas que solo se ven por HTTP. <b>Que cada tipo lleve sus campos y
 * ningun otro</b>, con 400 y no con 500. <b>Quien ve que</b>: el entrenador ve el
 * estado de los atletas de sus grupos, pero no registra ni ve el historial. Y
 * <b>que la subida de documentos no existe</b> con la configuracion por defecto,
 * que es la del despliegue inicial.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentDeliveryEndpointTest {

    private static final UUID CLUB = UUID.fromString("bbbb7777-0000-0000-0000-000000007711");
    private static final String SLUG = "club-entregas-it";
    private static final UUID CLUB_AJENO = UUID.fromString("bbbb7777-0000-0000-0000-000000007722");
    private static final String SLUG_AJENO = "club-entregas-ajeno-it";

    private static final String CLAVE = "clave-de-prueba-it";
    private static final String ADMIN = "admin_entregas_it";
    private static final String ENTRENADOR = "coach_entregas_it";
    private static final String SOCIO = "socio_entregas_it";

    private static final LocalDate HOY = LocalDate.now();
    private static final LocalDate INICIO = HOY.minusMonths(3);
    private static final LocalDate FIN = HOY.plusMonths(8);
    private static final LocalDate SALIDA = HOY.plusDays(20);
    private static final LocalDate VUELTA = HOY.plusDays(23);

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private final Map<String, String> tokens = new HashMap<>();

    private UUID temporada;
    private UUID temporadaPasada;
    /** Menor, en el grupo del entrenador. */
    private UUID ana;
    /** Menor, en ningun grupo del entrenador. */
    private UUID bruno;
    /** Mayor de edad, en el grupo del entrenador. */
    private UUID carlos;
    private UUID ajeno;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();

        crearClub(CLUB, SLUG);
        crearClub(CLUB_AJENO, SLUG_AJENO);
        crearUsuario(ADMIN, "ROLE_ADMIN");
        crearUsuario(ENTRENADOR, "ROLE_TECHNICAL_STAFF");
        crearUsuario(SOCIO, "ROLE_USER");

        temporada = crearTemporada(CLUB, "Temporada actual", INICIO, FIN, true);
        temporadaPasada = crearTemporada(CLUB, "Temporada pasada",
                INICIO.minusYears(1), INICIO.minusDays(1), false);
        crearTemporada(CLUB_AJENO, "Temporada ajena", INICIO, FIN, true);

        ana = crearAtleta(CLUB, "Ana", "60000001A", LocalDate.of(2013, 4, 17));
        bruno = crearAtleta(CLUB, "Bruno", "60000002B", LocalDate.of(2012, 2, 2));
        carlos = crearAtleta(CLUB, "Carlos", "60000003C", LocalDate.of(2000, 1, 1));
        ajeno = crearAtleta(CLUB_AJENO, "Dora", "60000004D", LocalDate.of(2013, 1, 1));

        UUID grupo = crearGrupoDelEntrenador();
        apuntar(ana, grupo);
        apuntar(carlos, grupo);
    }

    @BeforeEach
    void limpio() {
        modoPublico();
        jdbc.update("DELETE FROM document_deliveries WHERE club_id IN (?, ?)", CLUB, CLUB_AJENO);
    }

    @AfterAll
    void fin() {
        modoPublico();
        borrarTodo();
    }

    // ----------------------------------------------------------------
    //  1. Cada tipo lleva sus campos
    // ----------------------------------------------------------------

    @Test
    @DisplayName("se registran los tres tipos, cada uno con lo suyo")
    void losTresTipos() {
        assertThat(registrar(ana, licencia(temporada)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registrar(ana, identidad(HOY.plusYears(5))).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registrar(ana, permiso(SALIDA, VUELTA)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("una licencia sin temporada, o con fechas propias, es 400")
    void licenciaMalFormada() {
        assertThat(registrar(ana, "{\"type\":\"LICENSE_APPLICATION\",\"deliveredOn\":\"" + HOY + "\"}")
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(registrar(ana, "{\"type\":\"LICENSE_APPLICATION\",\"deliveredOn\":\"" + HOY + "\","
                + "\"seasonId\":\"" + temporada + "\",\"validUntil\":\"" + FIN + "\"}")
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("un documento de identidad sin caducidad es 400")
    void identidadSinCaducidad() {
        ResponseEntity<String> respuesta =
                registrar(ana, "{\"type\":\"IDENTITY_DOCUMENT\",\"deliveredOn\":\"" + HOY + "\"}");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).contains("caducidad");
    }

    @Test
    @DisplayName("un permiso de viaje con la vuelta antes de la salida es 400")
    void permisoAlReves() {
        assertThat(registrar(ana, permiso(VUELTA, SALIDA)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** Minimizacion: el permiso de un adulto no sirve para nada, y guardarlo seria guardar sus viajes. */
    @Test
    @DisplayName("no se registra permiso de viaje para un mayor de edad")
    void permisoDeAdulto() {
        ResponseEntity<String> respuesta = registrar(carlos, permiso(SALIDA, VUELTA));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).contains("mayor de edad");
    }

    @Test
    @DisplayName("la entrega no puede tener fecha futura")
    void entregaFutura() {
        String cuerpo = "{\"type\":\"IDENTITY_DOCUMENT\",\"deliveredOn\":\"" + HOY.plusDays(1) + "\","
                + "\"validUntil\":\"" + HOY.plusYears(5) + "\"}";

        assertThat(registrar(ana, cuerpo).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ----------------------------------------------------------------
    //  2. El estado se calcula
    // ----------------------------------------------------------------

    @Test
    @DisplayName("sin nada registrado, licencia y documento salen como MISSING")
    void sinNada() {
        assertThat(estado(ana, ADMIN))
                .contains("\"LICENSE_APPLICATION\":\"MISSING\"")
                .contains("\"IDENTITY_DOCUMENT\":\"MISSING\"");
    }

    @Test
    @DisplayName("con la licencia de este curso y un DNI en vigor, todo VALID")
    void enRegla() {
        registrar(ana, licencia(temporada));
        registrar(ana, identidad(HOY.plusYears(5)));

        assertThat(estado(ana, ADMIN))
                .contains("\"LICENSE_APPLICATION\":\"VALID\"")
                .contains("\"IDENTITY_DOCUMENT\":\"VALID\"");
    }

    /**
     * Es lo que el club necesita en septiembre: separar a quien hay que pedirle
     * que renueve de quien nunca ha traido ninguna.
     */
    @Test
    @DisplayName("si solo trajo la licencia del curso pasado, EXPIRED y no MISSING")
    void licenciaDelCursoPasado() {
        registrar(ana, licencia(temporadaPasada));

        assertThat(estado(ana, ADMIN)).contains("\"LICENSE_APPLICATION\":\"EXPIRED\"");
    }

    @Test
    @DisplayName("un DNI que caduca en diez días avisa, y uno caducado sale como tal")
    void caducidadDelDni() {
        registrar(ana, identidad(HOY.plusDays(10)));
        assertThat(estado(ana, ADMIN)).contains("\"IDENTITY_DOCUMENT\":\"EXPIRING_SOON\"");

        registrar(bruno, identidad(HOY.minusDays(1)));
        assertThat(estado(bruno, ADMIN)).contains("\"IDENTITY_DOCUMENT\":\"EXPIRED\"");
    }

    @Test
    @DisplayName("manda el documento que más lejos caduca, no el último anotado")
    void mandaElQueMasDura() {
        registrar(ana, identidad(HOY.plusYears(5)));
        registrar(ana, identidad(HOY.minusYears(1)));

        assertThat(estado(ana, ADMIN)).contains("\"IDENTITY_DOCUMENT\":\"VALID\"");
    }

    // ----------------------------------------------------------------
    //  3. Permiso de viaje
    // ----------------------------------------------------------------

    @Test
    @DisplayName("un menor sin permiso lo necesita y no lo tiene")
    void menorSinPermiso() {
        assertThat(viaje(ana, SALIDA, VUELTA, ADMIN))
                .contains("\"required\":true")
                .contains("\"covered\":false");
    }

    @Test
    @DisplayName("con un permiso que cubre el viaje entero, cubierto")
    void menorConPermiso() {
        registrar(ana, permiso(SALIDA.minusDays(1), VUELTA));

        assertThat(viaje(ana, SALIDA, VUELTA, ADMIN)).contains("\"covered\":true");
    }

    @Test
    @DisplayName("un permiso que no llega al día de vuelta no cubre el viaje")
    void permisoCorto() {
        registrar(ana, permiso(SALIDA, VUELTA.minusDays(1)));

        assertThat(viaje(ana, SALIDA, VUELTA, ADMIN)).contains("\"covered\":false");
    }

    @Test
    @DisplayName("un mayor de edad no necesita permiso")
    void adultoNoLoNecesita() {
        assertThat(viaje(carlos, SALIDA, VUELTA, ADMIN)).contains("\"required\":false");
    }

    // ----------------------------------------------------------------
    //  4. Quién ve qué
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el entrenador ve el estado y el permiso de los atletas de su grupo")
    void elEntrenadorVeLosSuyos() {
        assertThat(get("/api/document-deliveries/athlete/" + ana + "/status", ENTRENADOR).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get(rutaViaje(ana, SALIDA, VUELTA), ENTRENADOR).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("pero no los de un atleta que no está en ninguno de sus grupos")
    void elEntrenadorNoVeOtros() {
        assertThat(get("/api/document-deliveries/athlete/" + bruno + "/status", ENTRENADOR).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(rutaViaje(bruno, SALIDA, VUELTA), ENTRENADOR).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("el entrenador no registra entregas ni ve el historial con fechas")
    void elEntrenadorNoRegistra() {
        assertThat(post("/api/document-deliveries/athlete/" + ana, identidad(HOY.plusYears(5)), ENTRENADOR)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/document-deliveries/athlete/" + ana, ENTRENADOR).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("un socio sin rol no ve nada de esto")
    void elSocioNoVeNada() {
        assertThat(get("/api/document-deliveries/athlete/" + ana + "/status", SOCIO).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("un atleta de otro club es un 404, también para el administrador")
    void atletaAjeno() {
        assertThat(get("/api/document-deliveries/athlete/" + ajeno + "/status", ADMIN).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(registrar(ajeno, identidad(HOY.plusYears(5))).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("una licencia colgada de una temporada de otro club es un 404")
    void temporadaAjena() {
        modoPublico();
        UUID temporadaAjena = jdbc.queryForObject(
                "SELECT id FROM seasons WHERE club_id = ?", UUID.class, CLUB_AJENO);

        assertThat(registrar(ana, licencia(temporadaAjena)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ----------------------------------------------------------------
    //  5. La subida de archivos, apagada
    // ----------------------------------------------------------------

    /**
     * Con la configuracion por defecto —la del despliegue inicial— no se sube,
     * no se lista y no se descarga ningun archivo de atleta. Ni siquiera el
     * administrador: no es un permiso, es que la funcionalidad no esta.
     */
    @Test
    @DisplayName("con la configuración por defecto, los documentos subidos no existen para nadie")
    void subidaApagada() {
        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("file", new ByteArrayResource("contenido".getBytes()) {
            @Override
            public String getFilename() {
                return "prueba.png";
            }
        });
        cuerpo.add("title", "Ficha");
        cuerpo.add("type", "OTHER");
        HttpHeaders cabeceras = autorizacion(ADMIN);
        cabeceras.setContentType(MediaType.MULTIPART_FORM_DATA);

        assertThat(rest.exchange("/api/athlete-documents/athlete/" + ana, HttpMethod.POST,
                new HttpEntity<>(cuerpo, cabeceras), String.class).getStatusCode())
                .as("subir").isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/athlete-documents/athlete/" + ana, ADMIN).getStatusCode())
                .as("listar").isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/athlete-documents/" + UUID.randomUUID() + "/file", ADMIN).getStatusCode())
                .as("descargar").isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/athlete-documents/my", ADMIN).getStatusCode())
                .as("los míos").isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ----------------------------------------------------------------
    //  Andamiaje
    // ----------------------------------------------------------------

    private String licencia(UUID temporadaId) {
        return "{\"type\":\"LICENSE_APPLICATION\",\"deliveredOn\":\"" + HOY + "\","
                + "\"seasonId\":\"" + temporadaId + "\"}";
    }

    private String identidad(LocalDate caduca) {
        return "{\"type\":\"IDENTITY_DOCUMENT\",\"deliveredOn\":\"" + HOY + "\","
                + "\"validUntil\":\"" + caduca + "\"}";
    }

    private String permiso(LocalDate salida, LocalDate vuelta) {
        return "{\"type\":\"TRAVEL_PERMIT\",\"deliveredOn\":\"" + HOY + "\","
                + "\"validFrom\":\"" + salida + "\",\"validUntil\":\"" + vuelta + "\"}";
    }

    private ResponseEntity<String> registrar(UUID atleta, String cuerpo) {
        return post("/api/document-deliveries/athlete/" + atleta, cuerpo, ADMIN);
    }

    private String estado(UUID atleta, String usuario) {
        return get("/api/document-deliveries/athlete/" + atleta + "/status", usuario).getBody();
    }

    private String rutaViaje(UUID atleta, LocalDate salida, LocalDate vuelta) {
        return "/api/document-deliveries/athlete/" + atleta + "/travel-permit?from=" + salida + "&to=" + vuelta;
    }

    private String viaje(UUID atleta, LocalDate salida, LocalDate vuelta, String usuario) {
        return get(rutaViaje(atleta, salida, vuelta), usuario).getBody();
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void borrarTodo() {
        jdbc.update("DELETE FROM document_deliveries WHERE club_id IN (?, ?)", CLUB, CLUB_AJENO);
        jdbc.update("DELETE FROM athlete_groups WHERE athlete_id IN"
                + " (SELECT id FROM athletes WHERE club_id IN (?, ?))", CLUB, CLUB_AJENO);
        jdbc.update("DELETE FROM training_groups WHERE club_id IN (?, ?)", CLUB, CLUB_AJENO);
        jdbc.update("DELETE FROM seasons WHERE club_id IN (?, ?)", CLUB, CLUB_AJENO);
        jdbc.update("DELETE FROM athletes WHERE club_id IN (?, ?)", CLUB, CLUB_AJENO);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN"
                + " (SELECT id FROM users WHERE club_id IN (?, ?))", CLUB, CLUB_AJENO);
        jdbc.update("DELETE FROM users WHERE club_id IN (?, ?)", CLUB, CLUB_AJENO);
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

    private UUID crearTemporada(UUID club, String nombre, LocalDate inicio, LocalDate fin, boolean activa) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO seasons"
                        + " (id, club_id, name, start_date, end_date, active, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, now(), now())",
                id, club, nombre, inicio, fin, activa);
        return id;
    }

    private UUID crearAtleta(UUID club, String nombre, String dni, LocalDate nacimiento) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO athletes"
                        + " (id, club_id, first_name, last_name, birth_date, dni, gender_id,"
                        + "  created_at, updated_at)"
                        + " SELECT ?, ?, ?, 'Nadadora', ?, ?, g.id, now(), now()"
                        + " FROM genders g WHERE g.name = 'FEMALE'",
                id, club, nombre, nacimiento, dni);
        return id;
    }

    private UUID crearGrupoDelEntrenador() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO training_groups"
                        + " (id, club_id, season_id, coach_id, name, category, level, created_at, updated_at)"
                        + " SELECT ?, ?, ?, u.id, 'Alevín A', 'ALEVIN', 'COMPETICION', now(), now()"
                        + " FROM users u WHERE u.club_id = ? AND u.username = ?",
                id, CLUB, temporada, CLUB, ENTRENADOR);
        return id;
    }

    private void apuntar(UUID atleta, UUID grupo) {
        jdbc.update("INSERT INTO athlete_groups (id, athlete_id, group_id, joined_on, created_at)"
                + " VALUES (?, ?, ?, ?, now())", UUID.randomUUID(), atleta, grupo, INICIO);
    }

    private ResponseEntity<String> get(String ruta, String usuario) {
        return rest.exchange(ruta, HttpMethod.GET, new HttpEntity<>(autorizacion(usuario)), String.class);
    }

    private ResponseEntity<String> post(String ruta, String cuerpo, String usuario) {
        HttpHeaders cabeceras = autorizacion(usuario);
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(ruta, HttpMethod.POST, new HttpEntity<>(cuerpo, cabeceras), String.class);
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
