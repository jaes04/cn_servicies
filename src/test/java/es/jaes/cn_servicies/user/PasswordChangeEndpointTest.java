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
 * Cambiar la contrasena, que hasta ahora no se podia de ninguna forma: solo se
 * fijaba al crear la cuenta, y una contrasena olvidada obligaba a borrar al
 * usuario y volver a crearlo, perdiendo sus vinculos con atletas.
 *
 * <p>Van juntas las dos rutas y el bloqueo de cuentas porque son la misma
 * historia: el administrador fija una contrasena que acaba sabiendo, el dueno
 * la cambia despues, y si hay que echar a alguien de verdad lo que sirve es
 * bloquear la cuenta —lo unico que corta una sesion ya abierta mientras no haya
 * lista de revocacion—.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PasswordChangeEndpointTest {

    private static final UUID CLUB = UUID.fromString("dddd9999-0000-0000-0000-000000009933");
    private static final String SLUG = "club-claves-usuario-it";
    private static final String ADMIN = "admin_claves_it";
    private static final String VIEJA = "clave-vieja-de-prueba";
    private static final String NUEVA = "clave-nueva-de-prueba";

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LoginAttemptService loginAttemptService;

    private String tokenDelAdmin;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Claves Usuario IT', ?, true, now())", CLUB, SLUG);
        crearUsuario(ADMIN, VIEJA, "ROLE_ADMIN");
        tokenDelAdmin = accessTokenDe(ADMIN, VIEJA);
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
    //  1. El administrador fija una contrasena olvidada
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el administrador fija la contraseña de otra cuenta y esa cuenta entra con ella")
    void elAdminFijaLaClave() {
        UUID usuario = crearUsuario("olvidadizo1", VIEJA, "ROLE_USER");

        assertThat(fijarClave(usuario, NUEVA, tokenDelAdmin).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(entrar("olvidadizo1", NUEVA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("al fijarla, la anterior deja de valer")
    void laAnteriorDejaDeValer() {
        UUID usuario = crearUsuario("olvidadizo2", VIEJA, "ROLE_USER");
        fijarClave(usuario, NUEVA, tokenDelAdmin);

        assertThat(entrar("olvidadizo2", VIEJA).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * A este lo sostienen <b>dos</b> capas: la regla de {@code /api/users/**} en
     * {@code SecurityConfig} y el {@code @PreAuthorize} del controlador.
     * Comprobado quitando cada una por separado: el test sigue verde con
     * cualquiera de las dos. Asi que no vale como prueba de ninguna en
     * concreto, y quien quite una de las dos no se enterara por aqui.
     */
    @Test
    @DisplayName("un usuario normal no puede fijar la contraseña de otro")
    void soloElAdminLaFija() {
        UUID victima = crearUsuario("victima", VIEJA, "ROLE_USER");
        crearUsuario("cualquiera", VIEJA, "ROLE_USER");
        String token = accessTokenDe("cualquiera", VIEJA);

        assertThat(fijarClave(victima, NUEVA, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(entrar("victima", VIEJA).getStatusCode())
                .as("la contraseña de la víctima no puede haber cambiado")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("fijar la contraseña de una cuenta que no existe es 404")
    void cuentaInexistente() {
        assertThat(fijarClave(UUID.randomUUID(), NUEVA, tokenDelAdmin).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("una contraseña demasiado corta es 400")
    void claveCorta() {
        UUID usuario = crearUsuario("corta", VIEJA, "ROLE_USER");

        ResponseEntity<String> respuesta = fijarClave(usuario, "corta", tokenDelAdmin);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).contains("8 caracteres");
    }

    // ----------------------------------------------------------------
    //  2. Cada uno cambia la suya
    // ----------------------------------------------------------------

    @Test
    @DisplayName("un usuario normal cambia su contraseña dando la actual")
    void cambioPropio() {
        crearUsuario("propia1", VIEJA, "ROLE_USER");
        String token = accessTokenDe("propia1", VIEJA);

        assertThat(cambiarMiClave(VIEJA, NUEVA, token).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(entrar("propia1", NUEVA).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entrar("propia1", VIEJA).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("con la contraseña actual equivocada es 400 y no cambia nada")
    void laActualTieneQueSerCorrecta() {
        crearUsuario("propia2", VIEJA, "ROLE_USER");
        String token = accessTokenDe("propia2", VIEJA);

        ResponseEntity<String> respuesta = cambiarMiClave("no-es-esta", NUEVA, token);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).contains("actual no es correcta");
        assertThat(entrar("propia2", VIEJA).getStatusCode())
                .as("un token robado no puede bastar para quedarse con la cuenta")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("repetir la misma contraseña es 400")
    void laNuevaTieneQueSerDistinta() {
        crearUsuario("propia3", VIEJA, "ROLE_USER");
        String token = accessTokenDe("propia3", VIEJA);

        ResponseEntity<String> respuesta = cambiarMiClave(VIEJA, VIEJA, token);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).contains("distinta");
    }

    @Test
    @DisplayName("sin token no se cambia ninguna contraseña")
    void sinTokenNo() {
        assertThat(cambiarMiClave(VIEJA, NUEVA, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("la contraseña que fija el administrador la puede cambiar después su dueño")
    void elDuenoRecuperaElControl() {
        UUID usuario = crearUsuario("recupera", VIEJA, "ROLE_USER");
        fijarClave(usuario, NUEVA, tokenDelAdmin);
        String token = accessTokenDe("recupera", NUEVA);

        assertThat(cambiarMiClave(NUEVA, "solo-la-se-yo-12", token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(entrar("recupera", "solo-la-se-yo-12").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ----------------------------------------------------------------
    //  3. Bloquear una cuenta corta la sesion abierta
    // ----------------------------------------------------------------

    @Test
    @DisplayName("bloquear una cuenta invalida su token en el acto")
    void bloquearCortaElToken() {
        crearUsuario("bloqueame1", VIEJA, "ROLE_USER");
        String token = accessTokenDe("bloqueame1", VIEJA);
        assertThat(yo(token).getStatusCode()).isEqualTo(HttpStatus.OK);

        bloquear("bloqueame1");

        assertThat(yo(token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("una cuenta bloqueada tampoco puede renovar su token")
    void bloquearCortaLaRenovacion() {
        crearUsuario("bloqueame2", VIEJA, "ROLE_USER");
        String refresco = sacar(entrar("bloqueame2", VIEJA), "refreshToken");

        bloquear("bloqueame2");

        ResponseEntity<String> respuesta = refrescar(refresco);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(respuesta.getBody()).contains("bloqueada");
    }

    // ----------------------------------------------------------------

    private ResponseEntity<String> fijarClave(UUID id, String clave, String token) {
        return rest.exchange("/api/users/" + id + "/password", HttpMethod.PUT,
                new HttpEntity<>("{\"password\":\"" + clave + "\"}", cabeceras(token)), String.class);
    }

    private ResponseEntity<String> cambiarMiClave(String actual, String nueva, String token) {
        String cuerpo = "{\"currentPassword\":\"" + actual + "\",\"newPassword\":\"" + nueva + "\"}";
        return rest.exchange("/api/users/me/password", HttpMethod.PUT,
                new HttpEntity<>(cuerpo, cabeceras(token)), String.class);
    }

    private ResponseEntity<String> entrar(String usuario, String clave) {
        String cuerpo = "{\"username\":\"" + usuario + "\",\"password\":\"" + clave + "\"}";
        return rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(cuerpo, cabeceras(null)), String.class);
    }

    private ResponseEntity<String> refrescar(String token) {
        return rest.exchange("/api/auth/refresh", HttpMethod.POST,
                new HttpEntity<>("{\"refreshToken\":\"" + token + "\"}", cabeceras(null)), String.class);
    }

    private ResponseEntity<String> yo(String token) {
        return rest.exchange("/api/users/me", HttpMethod.GET,
                new HttpEntity<>(null, cabeceras(token)), String.class);
    }

    private HttpHeaders cabeceras(String token) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            cabeceras.setBearerAuth(token);
        }
        return cabeceras;
    }

    private String accessTokenDe(String usuario, String clave) {
        return sacar(entrar(usuario, clave), "accessToken");
    }

    private String sacar(ResponseEntity<String> respuesta, String campo) {
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta.getBody().replaceAll(".*\"" + campo + "\":\"([^\"]+)\".*", "$1");
    }

    private UUID crearUsuario(String username, String clave, String rol) {
        UUID id = UUID.randomUUID();
        modoPublico();
        jdbc.update("INSERT INTO users"
                        + " (id, club_id, username, email, password_hash, blocked, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, false, now(), now())",
                id, CLUB, username, username + "@it.local", passwordEncoder.encode(clave));
        jdbc.update("INSERT INTO user_roles (user_id, role_id)"
                + " SELECT ?, id FROM roles WHERE name = ?", id, rol);
        return id;
    }

    private void bloquear(String username) {
        modoPublico();
        jdbc.update("UPDATE users SET blocked = true WHERE club_id = ? AND username = ?", CLUB, username);
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
