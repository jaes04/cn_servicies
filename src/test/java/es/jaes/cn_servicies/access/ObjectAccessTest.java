package es.jaes.cn_servicies.access;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea S.3.3: autorizacion a nivel de objeto.
 *
 * <p>Lo que se prueba aqui no lo cubre ninguna de las capas de la Fase 0. El
 * filtro de Hibernate y las policies de RLS responden "¿es de mi club?"; estos
 * tests preguntan <b>"¿es mio?"</b> dentro de un mismo club, que es donde el
 * aislamiento por tenant no dice nada.
 *
 * <p>El caso que importa es el primero: {@code athlete_documents} es tabla hija,
 * asi que no lleva {@code club_id} ni policy, y su id viaja suelto en la URL de
 * descarga. Antes de esta tarea, ese id era lo unico que hacia falta para abrir
 * el documento de cualquier atleta — {@code MEDICAL} incluido, que es lo mas
 * sensible que guarda el sistema. Ver {@code docs/rgpd.md} §1.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ObjectAccessTest {

    private static final UUID CLUB = UUID.fromString("aaaa5555-0000-0000-0000-000000005511");
    private static final String SLUG = "club-acceso-objeto-it";

    private static final UUID CLUB_AJENO = UUID.fromString("aaaa5555-0000-0000-0000-000000005522");
    private static final String SLUG_AJENO = "club-acceso-objeto-ajeno-it";

    private static final String CLAVE = "clave-de-prueba-it";
    private static final String ADMIN = "admin_acceso_it";
    private static final String ENTRENADOR = "coach_acceso_it";
    private static final String TUTOR_ANA = "tutor_ana_acceso_it";
    private static final String TUTOR_BRUNO = "tutor_bruno_acceso_it";
    private static final String ADMIN_AJENO = "admin_ajeno_acceso_it";

    private static final LocalDate NACIMIENTO = LocalDate.of(2013, 4, 17);

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${app.upload.dir}") private String uploadDir;

    private final Map<String, String> tokens = new HashMap<>();
    private final Map<String, UUID> usuarios = new HashMap<>();

    private UUID ana;
    private UUID docDeAna;
    private UUID docAjeno;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();

        crearClub(CLUB, SLUG);
        crearClub(CLUB_AJENO, SLUG_AJENO);

        crearUsuario(CLUB, ADMIN, "ROLE_ADMIN");
        crearUsuario(CLUB, ENTRENADOR, "ROLE_TECHNICAL_STAFF");
        crearUsuario(CLUB, TUTOR_ANA, "ROLE_USER");
        crearUsuario(CLUB, TUTOR_BRUNO, "ROLE_USER");
        crearUsuario(CLUB_AJENO, ADMIN_AJENO, "ROLE_ADMIN");

        ana = crearAtleta(CLUB, "Ana", "30000001A");
        UUID bruno = crearAtleta(CLUB, "Bruno", "30000002B");
        UUID ajeno = crearAtleta(CLUB_AJENO, "Carla", "30000003C");

        vincular(TUTOR_ANA, ana);
        vincular(TUTOR_BRUNO, bruno);

        // El de Ana es MEDICAL a proposito: es el peor caso de la fuga que
        // cierra esta tarea.
        docDeAna = crearDocumento(ana, ADMIN, "MEDICAL");
        docAjeno = crearDocumento(ajeno, ADMIN_AJENO, "OTHER");
    }

    @AfterAll
    void fin() {
        modoPublico();
        borrarTodo();
    }

    // ----------------------------------------------------------------
    //  1. Documentos: el id suelto ya no basta
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el tutor abre el documento de su hija")
    void elTutorAbreElSuyo() {
        assertThat(get(archivo(docDeAna), TUTOR_ANA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * El agujero que cierra la tarea. Mismo club, cuenta corriente, un id de
     * documento: hasta ahora eso abria el archivo de cualquier atleta.
     */
    @Test
    @DisplayName("el tutor de Bruno no abre el documento MEDICAL de Ana, aunque sea del mismo club")
    void elTutorNoAbreElDeOtro() {
        assertThat(get(archivo(docDeAna), TUTOR_BRUNO).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * 404 y no 403: un 403 confirmaria que ese documento existe, que es
     * justamente lo que no tiene por que saber. {@code docs/convenciones.md}.
     */
    @Test
    @DisplayName("el documento ajeno se niega igual que uno inexistente")
    void ajenoEInexistenteSeParecen() {
        var ajeno = get(archivo(docDeAna), TUTOR_BRUNO).getStatusCode();
        var inventado = get(archivo(UUID.randomUUID()), TUTOR_BRUNO).getStatusCode();

        assertThat(ajeno).isEqualTo(inventado).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("el entrenador sigue abriendo los documentos del club: acotarlo a sus grupos es el bloque siguiente")
    void elEntrenadorSigueEntrando() {
        assertThat(get(archivo(docDeAna), ENTRENADOR).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("el administrador de otro club no abre el documento, y su tabla no tiene policy que lo tape")
    void elAdminAjenoNoEntra() {
        assertThat(get(archivo(docAjeno), ADMIN).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("el borrado de un documento de otro club tampoco pasa, y la fila sigue ahí")
    void elBorradoAjenoNoPasa() {
        ResponseEntity<String> respuesta = rest.exchange(
                "/api/athlete-documents/" + docAjeno, HttpMethod.DELETE,
                new HttpEntity<>(autorizacion(ADMIN)), String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        modoPublico();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM athlete_documents WHERE id = ?", Integer.class, docAjeno))
                .isEqualTo(1);
    }

    // ----------------------------------------------------------------
    //  2. Subida: la misma regla, ahora en un solo sitio
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el tutor sube un documento a la ficha de su hija")
    void elTutorSubeALaSuya() {
        assertThat(subir(ana, TUTOR_ANA).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("el tutor de Bruno no sube nada a la ficha de Ana")
    void elTutorNoSubeALaDeOtro() {
        assertThat(subir(ana, TUTOR_BRUNO).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ----------------------------------------------------------------
    //  3. Cuentas: la foto de perfil es de quien la tiene
    // ----------------------------------------------------------------

    @Test
    @DisplayName("cada uno cambia su propia foto")
    void laPropiaSi() {
        assertThat(subirFoto(usuarios.get(TUTOR_ANA), TUTOR_ANA).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("nadie cambia la foto de otra cuenta")
    void laDeOtroNo() {
        assertThat(subirFoto(usuarios.get(TUTOR_ANA), TUTOR_BRUNO).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("el administrador sí puede, dentro de su club")
    void elAdminSi() {
        assertThat(subirFoto(usuarios.get(TUTOR_ANA), ADMIN).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    /**
     * A este no lo sostiene el guardian sino RLS: {@code users} lleva
     * {@code club_id} y policy, asi que la carga por id ya no encuentra la fila.
     * Se comprobo desactivando el guardian: los otros cinco negativos se ponen
     * rojos y este se queda verde. Esta aqui porque describe el limite —hasta
     * donde llega cada capa— no porque pruebe lo de esta tarea.
     */
    @Test
    @DisplayName("pero no sobre una cuenta de otro club: eso ya lo tapaba RLS")
    void elAdminAjenoNo() {
        assertThat(subirFoto(usuarios.get(TUTOR_ANA), ADMIN_AJENO).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ----------------------------------------------------------------
    //  Andamiaje
    // ----------------------------------------------------------------

    private String archivo(UUID documento) {
        return "/api/athlete-documents/" + documento + "/file";
    }

    private ResponseEntity<String> subir(UUID atleta, String usuario) {
        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("file", parte("prueba.png"));
        cuerpo.add("title", "Ficha");
        cuerpo.add("type", "OTHER");

        return multipart("/api/athlete-documents/athlete/" + atleta, cuerpo, usuario);
    }

    private ResponseEntity<String> subirFoto(UUID cuenta, String usuario) {
        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("file", parte("foto.png"));

        return multipart("/api/users/" + cuenta + "/profile-photo", cuerpo, usuario);
    }

    private ByteArrayResource parte(String nombre) {
        return new ByteArrayResource("contenido de prueba".getBytes()) {
            @Override
            public String getFilename() {
                return nombre;
            }
        };
    }

    private ResponseEntity<String> multipart(String ruta, MultiValueMap<String, Object> cuerpo, String usuario) {
        HttpHeaders cabeceras = autorizacion(usuario);
        cabeceras.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(ruta, HttpMethod.POST, new HttpEntity<>(cuerpo, cabeceras), String.class);
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void borrarTodo() {
        borrarArchivos();
        jdbc.update("DELETE FROM athlete_documents WHERE athlete_id IN"
                + " (SELECT id FROM athletes WHERE club_id IN (?, ?))", CLUB, CLUB_AJENO);
        jdbc.update("DELETE FROM user_athletes WHERE athlete_id IN"
                + " (SELECT id FROM athletes WHERE club_id IN (?, ?))", CLUB, CLUB_AJENO);
        jdbc.update("DELETE FROM athletes WHERE club_id IN (?, ?)", CLUB, CLUB_AJENO);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN"
                + " (SELECT id FROM users WHERE club_id IN (?, ?))", CLUB, CLUB_AJENO);
        jdbc.update("DELETE FROM users WHERE club_id IN (?, ?)", CLUB, CLUB_AJENO);
        jdbc.update("DELETE FROM clubs WHERE id IN (?, ?)", CLUB, CLUB_AJENO);
    }

    /**
     * Los archivos que ha dejado el test, tanto los sembrados como los que
     * subieron los propios casos. {@code uploads/} esta en {@code .gitignore},
     * asi que no se cuelan en un commit, pero tampoco tienen por que quedarse.
     */
    private void borrarArchivos() {
        List<String> nombres = jdbc.queryForList(
                "SELECT filename FROM athlete_documents WHERE athlete_id IN"
                        + " (SELECT id FROM athletes WHERE club_id IN (?, ?))"
                        + " UNION ALL"
                        + " SELECT profile_photo FROM users"
                        + " WHERE club_id IN (?, ?) AND profile_photo IS NOT NULL",
                String.class, CLUB, CLUB_AJENO, CLUB, CLUB_AJENO);

        for (String nombre : nombres) {
            try {
                Files.deleteIfExists(Paths.get(uploadDir).toAbsolutePath().normalize().resolve(nombre));
            } catch (IOException ignorado) {
                // Un archivo que no se puede borrar no invalida el test.
            }
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
        usuarios.put(usuario, id);
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

    private void vincular(String usuario, UUID atleta) {
        jdbc.update("INSERT INTO user_athletes (id, user_id, athlete_id, type, created_at)"
                        + " VALUES (?, ?, ?, 'TUTOR', now())",
                UUID.randomUUID(), usuarios.get(usuario), atleta);
    }

    /** Siembra el documento y su archivo: la descarga permitida tiene que devolver algo de verdad. */
    private UUID crearDocumento(UUID atleta, String subidoPor, String tipo) {
        UUID id = UUID.randomUUID();
        String nombre = UUID.randomUUID() + ".pdf";

        try {
            Path destino = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(destino);
            Files.write(destino.resolve(nombre), "documento de prueba".getBytes());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo sembrar el archivo del test", e);
        }

        jdbc.update("INSERT INTO athlete_documents"
                        + " (id, title, type, filename, original_filename, athlete_id, uploaded_by_id, created_at)"
                        + " VALUES (?, 'Documento de prueba', ?, ?, 'prueba.pdf', ?, ?, now())",
                id, tipo, nombre, atleta, usuarios.get(subidoPor));
        return id;
    }

    private ResponseEntity<String> get(String ruta, String usuario) {
        return rest.exchange(ruta, HttpMethod.GET,
                new HttpEntity<>(autorizacion(usuario)), String.class);
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
