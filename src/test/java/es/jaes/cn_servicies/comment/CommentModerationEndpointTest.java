package es.jaes.cn_servicies.comment;

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
 * Moderar comentarios: un comentario bloqueado desaparecia de la lista de su
 * noticia para todo el mundo, y con el nadie podia volver a encontrarlo para
 * desbloquearlo. Ahora quien modera lo pide con {@code includeBlocked=true}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommentModerationEndpointTest {

    private static final UUID CLUB = UUID.fromString("dddd9999-0000-0000-0000-000000009966");
    private static final String SLUG = "club-moderacion-it";
    private static final String CLAVE = "clave-de-prueba-it";

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LoginAttemptService loginAttemptService;

    private UUID noticia;
    private UUID bloqueado;
    private String tokenAdmin;
    private String tokenEditor;
    private String tokenSocio;

    @BeforeAll
    void inicio() {
        loginAttemptService.olvidarTodo();
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Moderacion IT', ?, true, now())", CLUB, SLUG);
        UUID admin = crearUsuario("admin_mod_it", "ROLE_ADMIN");
        crearUsuario("editor_mod_it", "ROLE_EDITOR");
        UUID socio = crearUsuario("socio_mod_it", "ROLE_USER");

        noticia = UUID.randomUUID();
        jdbc.update("INSERT INTO posts"
                        + " (id, club_id, title, content, slug, status, published_at, author_id,"
                        + "  created_at, updated_at)"
                        + " VALUES (?, ?, 'Noticia IT', 'Contenido de prueba', 'noticia-mod-it', 'PUBLISHED',"
                        + "  now(), ?, now(), now())",
                noticia, CLUB, admin);
        crearComentario("visible", socio, false, false);
        bloqueado = crearComentario("bloqueado", socio, true, false);
        crearComentario("borrado", socio, false, true);

        tokenAdmin = accessTokenDe("admin_mod_it");
        tokenEditor = accessTokenDe("editor_mod_it");
        tokenSocio = accessTokenDe("socio_mod_it");
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

    @Test
    @DisplayName("sin pedirlo, la lista no trae bloqueados ni borrados, tampoco al editor")
    void sinPedirloComoAntes() {
        ResponseEntity<String> respuesta = listar(false, tokenEditor);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("visible").doesNotContain("bloqueado").doesNotContain("borrado");
    }

    @Test
    @DisplayName("el editor pide los bloqueados y los ve, marcados; los borrados siguen sin salir")
    void elEditorVeLosBloqueados() {
        ResponseEntity<String> respuesta = listar(true, tokenEditor);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody())
                .contains("visible")
                .contains("bloqueado")
                .contains("\"blocked\":true")
                .doesNotContain("borrado");
    }

    @Test
    @DisplayName("el administrador también")
    void elAdminVeLosBloqueados() {
        ResponseEntity<String> respuesta = listar(true, tokenAdmin);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("bloqueado");
    }

    @Test
    @DisplayName("un usuario normal que pide los bloqueados recibe 403")
    void unUsuarioNoLosVe() {
        assertThat(listar(true, tokenSocio).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("el editor encuentra el bloqueado y lo desbloquea: vuelve a la lista de todos")
    void elEditorLoDesbloquea() {
        ResponseEntity<String> respuesta = rest.exchange(
                "/api/posts/" + noticia + "/comments/" + bloqueado + "/unblock", HttpMethod.PATCH,
                new HttpEntity<>(null, cabeceras(tokenEditor)), String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(listar(false, tokenSocio).getBody()).contains("bloqueado");

        // Lo deja como estaba para el resto de la clase.
        modoPublico();
        jdbc.update("UPDATE comments SET blocked = true WHERE id = ?", bloqueado);
    }

    // ----------------------------------------------------------------

    private ResponseEntity<String> listar(boolean includeBlocked, String token) {
        String url = "/api/posts/" + noticia + "/comments" + (includeBlocked ? "?includeBlocked=true" : "");
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(null, cabeceras(token)), String.class);
    }

    private UUID crearComentario(String texto, UUID autor, boolean blocked, boolean borrado) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO comments (id, content, post_id, author_id, blocked, deleted_at,"
                        + " created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, " + (borrado ? "now()" : "NULL") + ", now(), now())",
                id, "comentario " + texto, noticia, autor, blocked);
        return id;
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

    private UUID crearUsuario(String username, String rol) {
        UUID id = UUID.randomUUID();
        modoPublico();
        jdbc.update("INSERT INTO users"
                        + " (id, club_id, username, email, password_hash, blocked, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, false, now(), now())",
                id, CLUB, username, username + "@it.local", passwordEncoder.encode(CLAVE));
        jdbc.update("INSERT INTO user_roles (user_id, role_id)"
                + " SELECT ?, id FROM roles WHERE name = ?", id, rol);
        return id;
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void borrarTodo() {
        jdbc.update("DELETE FROM comments WHERE post_id IN (SELECT id FROM posts WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM posts WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN"
                + " (SELECT id FROM users WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM users WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM clubs WHERE id = ?", CLUB);
    }
}
