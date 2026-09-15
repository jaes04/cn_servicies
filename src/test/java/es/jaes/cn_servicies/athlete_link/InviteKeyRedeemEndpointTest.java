package es.jaes.cn_servicies.athlete_link;

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
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El canje de una clave de invitacion y sus errores.
 *
 * <p>Hasta ahora tres de ellos —clave usada, caducada y usuario ya vinculado—
 * lanzaban {@code IllegalStateException}, que el manejador de errores no trata:
 * salian como 500, con el nombre de la excepcion delante del mensaje. Son errores
 * del usuario, no del servidor, y la pantalla tiene que poder distinguirlos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InviteKeyRedeemEndpointTest {

    private static final UUID CLUB = UUID.fromString("dddd9999-0000-0000-0000-000000009911");
    private static final String SLUG = "club-claves-it";
    private static final String CLAVE = "clave-de-prueba-it";
    private static final String SOCIO = "socio_claves_it";

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID socio;
    private String token;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Claves IT', ?, true, now())", CLUB, SLUG);
        socio = UUID.randomUUID();
        jdbc.update("INSERT INTO users"
                        + " (id, club_id, username, email, password_hash, blocked, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, false, now(), now())",
                socio, CLUB, SOCIO, SOCIO + "@it.local", passwordEncoder.encode(CLAVE));
        jdbc.update("INSERT INTO user_roles (user_id, role_id)"
                + " SELECT ?, id FROM roles WHERE name = 'ROLE_USER'", socio);
        token = iniciarSesion();
    }

    @AfterAll
    void fin() {
        modoPublico();
        borrarTodo();
    }

    @Test
    @DisplayName("una clave válida se canjea")
    void valida() {
        String clave = crearClave(crearAtleta("70000001A"), LocalDateTime.now().plusHours(1), false);

        assertThat(canjear(clave).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("una clave ya usada es 400, no 500")
    void usada() {
        String clave = crearClave(crearAtleta("70000002B"), LocalDateTime.now().plusHours(1), true);

        esErrorDeUsuario(canjear(clave), "utilizada");
    }

    @Test
    @DisplayName("una clave caducada es 400, no 500")
    void caducada() {
        String clave = crearClave(crearAtleta("70000003C"), LocalDateTime.now().minusHours(1), false);

        esErrorDeUsuario(canjear(clave), "expirado");
    }

    @Test
    @DisplayName("canjear la clave de un atleta con el que ya se tiene vínculo es 400, no 500")
    void yaVinculado() {
        UUID atleta = crearAtleta("70000004D");
        jdbc.update("INSERT INTO user_athletes (id, user_id, athlete_id, type, created_at)"
                + " VALUES (?, ?, ?, 'TUTOR', now())", UUID.randomUUID(), socio, atleta);
        String clave = crearClave(atleta, LocalDateTime.now().plusHours(1), false);

        esErrorDeUsuario(canjear(clave), "vinculado");
    }

    @Test
    @DisplayName("una clave que no existe es 400")
    void inexistente() {
        esErrorDeUsuario(canjear(UUID.randomUUID().toString()), "no válida");
    }

    // ----------------------------------------------------------------

    private void esErrorDeUsuario(ResponseEntity<String> respuesta, String mensaje) {
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody())
                .contains(mensaje)
                .as("el mensaje no puede llevar el nombre de la excepción delante")
                .doesNotContain("Exception");
    }

    private ResponseEntity<String> canjear(String clave) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(token);
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/athlete-links/redeem", HttpMethod.POST,
                new HttpEntity<>("{\"key\":\"" + clave + "\"}", cabeceras), String.class);
    }

    private UUID crearAtleta(String dni) {
        UUID id = UUID.randomUUID();
        modoPublico();
        jdbc.update("INSERT INTO athletes"
                        + " (id, club_id, first_name, last_name, birth_date, dni, gender_id, created_at, updated_at)"
                        + " SELECT ?, ?, 'Atleta', 'Claves', ?, ?, g.id, now(), now()"
                        + " FROM genders g WHERE g.name = 'FEMALE'",
                id, CLUB, LocalDate.of(2000, 1, 1), dni);
        return id;
    }

    private String crearClave(UUID atleta, LocalDateTime caduca, boolean usada) {
        String clave = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO athlete_invite_keys"
                        + " (id, key_value, athlete_id, type, expires_at, used, created_at)"
                        + " VALUES (?, ?, ?, 'TUTOR', ?, ?, now())",
                UUID.randomUUID(), clave, atleta, caduca, usada);
        return clave;
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void borrarTodo() {
        jdbc.update("DELETE FROM athlete_invite_keys WHERE athlete_id IN"
                + " (SELECT id FROM athletes WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM user_athletes WHERE athlete_id IN"
                + " (SELECT id FROM athletes WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM athletes WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN"
                + " (SELECT id FROM users WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM users WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM clubs WHERE id = ?", CLUB);
    }

    private String iniciarSesion() {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        String cuerpo = "{\"username\":\"" + SOCIO + "\",\"password\":\"" + CLAVE + "\"}";
        ResponseEntity<String> respuesta = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(cuerpo, cabeceras), String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta.getBody().replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}
