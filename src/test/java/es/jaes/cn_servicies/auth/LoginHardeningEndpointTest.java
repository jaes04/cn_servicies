package es.jaes.cn_servicies.auth;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que pasa en la puerta: tipos de token, limite de intentos y cuentas
 * bloqueadas.
 *
 * <p>Tres agujeros que cierra, y los tres se veian desde fuera:
 *
 * <ul>
 *   <li>El token de refresco —que dura una semana— servia tambien como token de
 *       acceso, y el de acceso servia para pedir uno nuevo indefinidamente, con
 *       lo que su caducidad corta no protegia de nada.
 *   <li>No habia limite de intentos: se podian probar contrasenas sin freno.
 *   <li>Una cuenta bloqueada por el club daba un <b>500</b> al intentar entrar.
 * </ul>
 *
 * <p>Cada prueba arranca con el contador a cero. Si no, el limite por IP se
 * arrastraria entre pruebas —todas salen de 127.0.0.1— y de paso dejaria
 * bloqueadas a las clases que corren despues.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoginHardeningEndpointTest {

    private static final UUID CLUB = UUID.fromString("dddd9999-0000-0000-0000-000000009922");
    private static final String SLUG = "club-login-it";
    private static final String USUARIO = "socio_login_it";
    private static final String BLOQUEADO = "bloqueado_login_it";
    private static final String CLAVE = "clave-de-prueba-it";

    /** Igual que el valor por defecto de {@code app.login.max-attempts-per-user}. */
    private static final int INTENTOS = 5;

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LoginAttemptService loginAttemptService;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Login IT', ?, true, now())", CLUB, SLUG);
        crearUsuario(USUARIO, false);
        crearUsuario(BLOQUEADO, true);
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
    //  1. El login normal
    // ----------------------------------------------------------------

    @Test
    @DisplayName("la contraseña correcta devuelve los dos tokens")
    void loginCorrecto() {
        ResponseEntity<String> respuesta = entrar(USUARIO, CLAVE);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("accessToken").contains("refreshToken");
    }

    @Test
    @DisplayName("la contraseña incorrecta es 401 y no dice si el usuario existe")
    void loginIncorrecto() {
        ResponseEntity<String> conocido = entrar(USUARIO, "esta-no-es");
        ResponseEntity<String> inventado = entrar("no_existe_nadie_asi", "esta-no-es");

        assertThat(conocido.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(inventado.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(mensajeDe(conocido)).isEqualTo(mensajeDe(inventado));
    }

    // ----------------------------------------------------------------
    //  2. Limite de intentos
    // ----------------------------------------------------------------

    @Test
    @DisplayName("tras agotar los intentos el login responde 429 y dice cuánto esperar")
    void demasiadosIntentos() {
        fallar(USUARIO, INTENTOS);

        ResponseEntity<String> respuesta = entrar(USUARIO, "otra-mas");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(respuesta.getBody()).contains("retryAfterSeconds");
        assertThat(respuesta.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();
    }

    @Test
    @DisplayName("bloqueado el usuario, ni la contraseña correcta entra")
    void elBloqueoNoLoSaltaLaClaveBuena() {
        fallar(USUARIO, INTENTOS);

        assertThat(entrar(USUARIO, CLAVE).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("hasta agotarlos, cada intento sigue siendo un 401 normal")
    void antesDeAgotarlosSigueSiendo401() {
        for (int i = 0; i < INTENTOS - 1; i++) {
            assertThat(entrar(USUARIO, "esta-no-es").getStatusCode())
                    .as("intento %d", i + 1)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    @DisplayName("un acierto por el medio borra los fallos acumulados")
    void elAciertoBorraElContador() {
        fallar(USUARIO, INTENTOS - 1);
        assertThat(entrar(USUARIO, CLAVE).getStatusCode()).isEqualTo(HttpStatus.OK);
        fallar(USUARIO, INTENTOS - 1);

        assertThat(entrar(USUARIO, CLAVE).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ----------------------------------------------------------------
    //  3. Cuenta bloqueada por el club
    // ----------------------------------------------------------------

    @Test
    @DisplayName("una cuenta bloqueada es 403 con su motivo, no 500")
    void cuentaBloqueada() {
        ResponseEntity<String> respuesta = entrar(BLOQUEADO, CLAVE);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(respuesta.getBody())
                .contains("bloqueada")
                .as("el mensaje no puede llevar el nombre de la excepción delante")
                .doesNotContain("Exception");
    }

    @Test
    @DisplayName("intentarlo con una cuenta bloqueada no gasta intentos: sigue diciendo por qué")
    void laCuentaBloqueadaNoGastaIntentos() {
        for (int i = 0; i < INTENTOS + 2; i++) {
            assertThat(entrar(BLOQUEADO, CLAVE).getStatusCode())
                    .as("intento %d", i + 1)
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ----------------------------------------------------------------
    //  4. Para que sirve cada token
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el refresh token renueva la sesión")
    void refrescoCorrecto() {
        ResponseEntity<String> respuesta = refrescar(sacar(entrar(USUARIO, CLAVE), "refreshToken"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("accessToken");
    }

    @Test
    @DisplayName("el access token no sirve para refrescar, y el error lo dice")
    void elAccessNoRefresca() {
        ResponseEntity<String> respuesta = refrescar(sacar(entrar(USUARIO, CLAVE), "accessToken"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(mensajeDe(respuesta)).contains("acceso");
    }

    @Test
    @DisplayName("un refresh token que no lo es devuelve 401")
    void refrescoConBasura() {
        assertThat(refrescar("esto-no-es-un-token").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("el access token autentica una petición normal")
    void elAccessAutentica() {
        String token = sacar(entrar(USUARIO, CLAVE), "accessToken");

        assertThat(yo(token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("el refresh token no autentica una petición normal")
    void elRefreshNoAutentica() {
        String token = sacar(entrar(USUARIO, CLAVE), "refreshToken");

        assertThat(yo(token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ----------------------------------------------------------------
    //  5. Coste de BCrypt
    // ----------------------------------------------------------------

    @Test
    @DisplayName("las contraseñas se guardan con BCrypt de coste 12")
    void bcryptDeCoste12() {
        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(passwordEncoder.encode(CLAVE)).startsWith("$2a$12$");
    }

    // ----------------------------------------------------------------

    private void fallar(String usuario, int veces) {
        for (int i = 0; i < veces; i++) {
            entrar(usuario, "esta-no-es");
        }
    }

    private ResponseEntity<String> entrar(String usuario, String clave) {
        String cuerpo = "{\"clubSlug\":\"" + SLUG + "\",\"username\":\"" + usuario + "\",\"password\":\"" + clave + "\"}";
        return rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(cuerpo, json()), String.class);
    }

    private ResponseEntity<String> refrescar(String token) {
        String cuerpo = "{\"refreshToken\":\"" + token + "\"}";
        return rest.exchange("/api/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(cuerpo, json()), String.class);
    }

    private ResponseEntity<String> yo(String token) {
        HttpHeaders cabeceras = json();
        cabeceras.setBearerAuth(token);
        return rest.exchange("/api/users/me", HttpMethod.GET,
                new HttpEntity<>(null, cabeceras), String.class);
    }

    private HttpHeaders json() {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return cabeceras;
    }

    private String sacar(ResponseEntity<String> respuesta, String campo) {
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta.getBody().replaceAll(".*\"" + campo + "\":\"([^\"]+)\".*", "$1");
    }

    private String mensajeDe(ResponseEntity<String> respuesta) {
        return respuesta.getBody().replaceAll(".*\"message\":\"([^\"]*)\".*", "$1");
    }

    private void crearUsuario(String username, boolean bloqueado) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users"
                        + " (id, club_id, username, email, password_hash, blocked, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, now(), now())",
                id, CLUB, username, username + "@it.local", passwordEncoder.encode(CLAVE), bloqueado);
        jdbc.update("INSERT INTO user_roles (user_id, role_id)"
                + " SELECT ?, id FROM roles WHERE name = 'ROLE_USER'", id);
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
