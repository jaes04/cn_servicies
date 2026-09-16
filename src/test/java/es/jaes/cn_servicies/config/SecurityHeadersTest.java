package es.jaes.cn_servicies.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las cabeceras de seguridad de las respuestas de la API.
 *
 * <p>Arranca con {@code server.forward-headers-strategy=framework}, que es como
 * va en produccion detras del tunel de Cloudflare: asi se puede simular una
 * peticion que llego por HTTPS con {@code X-Forwarded-Proto} y comprobar HSTS
 * por el mismo camino que la vera un navegador.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.forward-headers-strategy=framework")
class SecurityHeadersTest {

    /** Publica y sin autenticar: la cabecera tiene que estar aunque no haya sesion. */
    private static final String RUTA_PUBLICA = "/api/posts/published";

    @Autowired private TestRestTemplate rest;

    @Test
    @DisplayName("CSP de API: no carga nada y no se puede incrustar")
    void contentSecurityPolicy() {
        String csp = cabecera(peticion(RUTA_PUBLICA, false), "Content-Security-Policy");

        assertThat(csp)
                .as("una API JSON no necesita cargar nada")
                .contains("default-src 'none'")
                .contains("frame-ancestors 'none'");
    }

    @Test
    @DisplayName("X-Content-Type-Options: nosniff")
    void noAdivinarElTipo() {
        assertThat(cabecera(peticion(RUTA_PUBLICA, false), "X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    @DisplayName("X-Frame-Options: DENY")
    void noIncrustar() {
        assertThat(cabecera(peticion(RUTA_PUBLICA, false), "X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    @DisplayName("Referrer-Policy: no-referrer")
    void sinReferer() {
        assertThat(cabecera(peticion(RUTA_PUBLICA, false), "Referrer-Policy")).isEqualTo("no-referrer");
    }

    @Test
    @DisplayName("por HTTPS lleva HSTS de un año, subdominios incluidos")
    void hstsPorHttps() {
        String hsts = cabecera(peticion(RUTA_PUBLICA, true), "Strict-Transport-Security");

        assertThat(hsts).contains("max-age=31536000").contains("includeSubDomains");
    }

    /**
     * HSTS por HTTP no significa nada —el navegador la ignora— y emitirla ahi
     * solo confunde a quien lee las cabeceras buscando un problema.
     */
    @Test
    @DisplayName("por HTTP no lleva HSTS")
    void sinHstsPorHttp() {
        assertThat(peticion(RUTA_PUBLICA, false).getHeaders().containsKey("Strict-Transport-Security")).isFalse();
    }

    /**
     * Las respuestas de error de SecurityConfig se escriben a mano, con el
     * ObjectMapper, fuera de los controladores. Hay que comprobar que tambien
     * salen con las cabeceras y no solo las respuestas normales.
     */
    @Test
    @DisplayName("un 401 escrito a mano en SecurityConfig también lleva las cabeceras")
    void tambienEnLosErrores() {
        ResponseEntity<String> respuesta = peticion("/api/users/me", false);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(cabecera(respuesta, "Content-Security-Policy")).contains("default-src 'none'");
        assertThat(cabecera(respuesta, "X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(cabecera(respuesta, "Referrer-Policy")).isEqualTo("no-referrer");
    }

    // ----------------------------------------------------------------

    private ResponseEntity<String> peticion(String ruta, boolean https) {
        HttpHeaders cabeceras = new HttpHeaders();
        if (https) {
            cabeceras.set("X-Forwarded-Proto", "https");
        }
        return rest.exchange(ruta, HttpMethod.GET, new HttpEntity<>(cabeceras), String.class);
    }

    private String cabecera(ResponseEntity<String> respuesta, String nombre) {
        String valor = respuesta.getHeaders().getFirst(nombre);
        assertThat(valor).as("falta la cabecera %s", nombre).isNotNull();
        return valor;
    }
}
