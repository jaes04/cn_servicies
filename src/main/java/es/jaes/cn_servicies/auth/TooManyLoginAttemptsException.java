package es.jaes.cn_servicies.auth;

import java.time.Duration;

/**
 * Se han acumulado demasiados intentos fallidos y el login esta cerrado un rato.
 *
 * <p>Lleva cuanto queda para poder reintentar, porque la pantalla necesita
 * decirlo: un 401 generico haria que el usuario siguiera probando contrasenas
 * —alargando el bloqueo— convencido de que se equivoca al teclear.
 */
public class TooManyLoginAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyLoginAttemptsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
