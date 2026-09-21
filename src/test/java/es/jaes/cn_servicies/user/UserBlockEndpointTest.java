package es.jaes.cn_servicies.user;

import es.jaes.cn_servicies.auth.LoginAttemptService;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El editor bloquea cuentas, pero solo las de usuario.
 *
 * <p>Bloquear corta la sesion en el acto. Si el editor pudiera bloquear al
 * administrador, o a otro editor, podria dejar al club sin nadie que lo
 * deshiciera; por eso se le deja solo con las cuentas que no tienen mas rol que
 * {@code ROLE_USER}, y nunca con la suya.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserBlockEndpointTest {

    private static final UUID CLUB = UUID.fromString("dddd9999-0000-0000-0000-000000009955");
    private static final String SLUG = "club-bloqueo-it";
    private static final String CLAVE = "clave-de-prueba-it";
    private static final String ADMIN = "admin_bloqueo_it";
    private static final String EDITOR = "editor_bloqueo_it";

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LoginAttemptService loginAttemptService;

    private UUID idAdmin;
    private String tokenAdmin;
    private String tokenEditor;

    @BeforeAll
    void inicio() {
        loginAttemptService.olvidarTodo();
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Bloqueo IT', ?, true, now())", CLUB, SLUG);
        idAdmin = crearUsuario(ADMIN, "ROLE_ADMIN");
        crearUsuario(EDITOR, "ROLE_EDITOR");
        tokenAdmin = accessTokenDe(ADMIN);
        tokenEditor = accessTokenDe(EDITOR);
    }

    @BeforeEach
    void contadorALimpio() {
        loginAttemptService.olvidarTodo();
    }

    @AfterAll
    void fin() {
        loginAttemptService.olvidarTodo();
        modoPublico();
        borrarTodo();
    }

    // ----------------------------------------------------------------
    //  Lo que el editor si puede
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el editor bloquea una cuenta de usuario y su sesión se corta")
    void elEditorBloqueaUnUsuario() {
        UUID socio = crearUsuario("socio_bloq1", "ROLE_USER");
        String tokenSocio = accessTokenDe("socio_bloq1");

        assertThat(bloquear(socio, tokenEditor).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(estaBloqueado(socio)).isTrue();
        assertThat(yo(tokenSocio).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("el editor desbloquea una cuenta de usuario")
    void elEditorDesbloquea() {
        UUID socio = crearUsuario("socio_bloq2", "ROLE_USER");
        bloquear(socio, tokenEditor);

        assertThat(desbloquear(socio, tokenEditor).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(estaBloqueado(socio)).isFalse();
    }

    @Test
    @DisplayName("el editor ve el listado de cuentas, para poder encontrar a quién bloquear")
    void elEditorVeElListado() {
        ResponseEntity<String> respuesta = rest.exchange("/api/users?blocked=false", HttpMethod.GET,
                new HttpEntity<>(null, cabeceras(tokenEditor)), String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains(ADMIN);
    }

    // ----------------------------------------------------------------
    //  Lo que no
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el editor no puede bloquear al administrador")
    void elEditorNoBloqueaAlAdmin() {
        assertThat(bloquear(idAdmin, tokenEditor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(estaBloqueado(idAdmin)).isFalse();
    }

    @Test
    @DisplayName("el editor no puede bloquear a otro editor")
    void elEditorNoBloqueaAOtroEditor() {
        UUID otro = crearUsuario("editor_bloq2", "ROLE_EDITOR");

        assertThat(bloquear(otro, tokenEditor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(estaBloqueado(otro)).isFalse();
    }

    /**
     * Una cuenta con {@code ROLE_USER} y algo mas no es "de usuario": el rol que
     * cuenta es el mas alto. Sin esto, basta con que un entrenador tenga tambien
     * {@code ROLE_USER} para que el editor lo pueda dejar fuera.
     */
    @Test
    @DisplayName("ni a una cuenta que además de usuario es entrenador")
    void elEditorNoBloqueaAUnaCuentaConVariosRoles() {
        UUID mixta = crearUsuario("mixta_bloq", "ROLE_USER", "ROLE_TECHNICAL_STAFF");

        assertThat(bloquear(mixta, tokenEditor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(estaBloqueado(mixta)).isFalse();
    }

    @Test
    @DisplayName("el editor no puede desbloquear a quien el administrador bloqueó si no es usuario")
    void elEditorNoDesbloqueaAUnEditor() {
        UUID otro = crearUsuario("editor_bloq3", "ROLE_EDITOR");
        bloquear(otro, tokenAdmin);

        assertThat(desbloquear(otro, tokenEditor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(estaBloqueado(otro)).isTrue();
    }

    @Test
    @DisplayName("un usuario normal no bloquea a nadie")
    void unUsuarioNoBloquea() {
        UUID victima = crearUsuario("socio_bloq3", "ROLE_USER");
        crearUsuario("socio_bloq4", "ROLE_USER");
        String token = accessTokenDe("socio_bloq4");

        assertThat(bloquear(victima, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(estaBloqueado(victima)).isFalse();
    }

    @Test
    @DisplayName("un usuario normal sigue sin ver el listado de cuentas")
    void unUsuarioNoVeElListado() {
        crearUsuario("socio_bloq5", "ROLE_USER");
        String token = accessTokenDe("socio_bloq5");

        assertThat(rest.exchange("/api/users", HttpMethod.GET,
                new HttpEntity<>(null, cabeceras(token)), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("el editor sigue sin poder cambiar roles ni borrar cuentas")
    void elEditorNoHaceLoDemas() {
        UUID socio = crearUsuario("socio_bloq6", "ROLE_USER");

        assertThat(rest.exchange("/api/users/" + socio, HttpMethod.DELETE,
                new HttpEntity<>(null, cabeceras(tokenEditor)), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.exchange("/api/users/" + socio + "/roles", HttpMethod.PUT,
                new HttpEntity<>("{\"role\":\"ROLE_ADMIN\"}", cabeceras(tokenEditor)), String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ----------------------------------------------------------------
    //  El administrador, como antes
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el administrador sigue pudiendo bloquear a un editor")
    void elAdminBloqueaAUnEditor() {
        UUID otro = crearUsuario("editor_bloq4", "ROLE_EDITOR");

        assertThat(bloquear(otro, tokenAdmin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(estaBloqueado(otro)).isTrue();
    }

    @Test
    @DisplayName("bloquear una cuenta que no existe es 404, también para el editor")
    void cuentaInexistente() {
        assertThat(bloquear(UUID.randomUUID(), tokenEditor).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Con un editor propio y no el compartido: si esto fallara, el editor quedaria
     * bloqueado y arrastraria al resto de la clase.
     */
    @Test
    @DisplayName("el editor no puede bloquearse a sí mismo")
    void elEditorNoSeBloquea() {
        UUID yoMismo = crearUsuario("editor_bloq5", "ROLE_EDITOR");
        String token = accessTokenDe("editor_bloq5");

        assertThat(bloquear(yoMismo, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(estaBloqueado(yoMismo)).isFalse();
    }

    // ----------------------------------------------------------------

    private ResponseEntity<String> bloquear(UUID id, String token) {
        return rest.exchange("/api/users/" + id + "/block", HttpMethod.PATCH,
                new HttpEntity<>(null, cabeceras(token)), String.class);
    }

    private ResponseEntity<String> desbloquear(UUID id, String token) {
        return rest.exchange("/api/users/" + id + "/unblock", HttpMethod.PATCH,
                new HttpEntity<>(null, cabeceras(token)), String.class);
    }

    private ResponseEntity<String> yo(String token) {
        return rest.exchange("/api/users/me", HttpMethod.GET,
                new HttpEntity<>(null, cabeceras(token)), String.class);
    }

    private boolean estaBloqueado(UUID id) {
        modoPublico();
        return Boolean.TRUE.equals(
                jdbc.queryForObject("SELECT blocked FROM users WHERE id = ?", Boolean.class, id));
    }

    private HttpHeaders cabeceras(String token) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            cabeceras.setBearerAuth(token);
        }
        return cabeceras;
    }

    private String accessTokenDe(String usuario) {
        String cuerpo = "{\"clubSlug\":\"" + SLUG + "\",\"username\":\"" + usuario + "\",\"password\":\"" + CLAVE + "\"}";
        ResponseEntity<String> respuesta = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(cuerpo, cabeceras(null)), String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta.getBody().replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    private UUID crearUsuario(String username, String... roles) {
        UUID id = UUID.randomUUID();
        modoPublico();
        jdbc.update("INSERT INTO users"
                        + " (id, club_id, username, email, password_hash, blocked, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, false, now(), now())",
                id, CLUB, username, username + "@it.local", passwordEncoder.encode(CLAVE));
        for (String rol : roles) {
            jdbc.update("INSERT INTO user_roles (user_id, role_id)"
                    + " SELECT ?, id FROM roles WHERE name = ?", id, rol);
        }
        return id;
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void borrarTodo() {
        jdbc.update("DELETE FROM user_roles WHERE user_id IN"
                + " (SELECT id FROM users WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM users WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM clubs WHERE id = ?", CLUB);
    }
}
