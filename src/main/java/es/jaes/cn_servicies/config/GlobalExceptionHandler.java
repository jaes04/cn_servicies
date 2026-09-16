package es.jaes.cn_servicies.config;

import es.jaes.cn_servicies.auth.InvalidTokenException;
import es.jaes.cn_servicies.auth.TooManyLoginAttemptsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED,
                "Usuario o contraseña incorrectos", request.getRequestURI());
    }

    /**
     * La cuenta existe pero el club la ha bloqueado. Sale como 403 y no como
     * 401 porque la contrasena podria ser correcta: el problema no se arregla
     * volviendo a teclearla.
     *
     * <p>Sin este manejador acababa en el generico y salia un <b>500</b>:
     * {@code DisabledException} la lanzan las comprobaciones previas de Spring
     * Security y no es {@code BadCredentialsException}.
     *
     * <p><b>El mensaje admite que la cuenta existe</b>, a diferencia del de
     * credenciales incorrectas. Es deliberado: solo revela los usernames que el
     * club ha bloqueado a proposito, y el precio de ocultarlo es un bloqueado
     * que cambia su contrasena tres veces antes de llamar al club.
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, Object>> handleDisabled(HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN,
                "Esta cuenta está bloqueada. Ponte en contacto con el club", request.getRequestURI());
    }

    /**
     * El token del cuerpo no sirve: invalido, caducado o del tipo que no es.
     * 401 y no 400, porque lo que toca es volver a identificarse.
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidToken(
            InvalidTokenException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Demasiados intentos fallidos de login.
     *
     * <p>Lleva {@code retryAfterSeconds} en el cuerpo y la cabecera estandar
     * {@code Retry-After}, para que la pantalla pueda enseniar una cuenta atras
     * en vez de dejar al usuario probando contrasenas que ni se comprueban.
     */
    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyAttempts(
            TooManyLoginAttemptsException ex, HttpServletRequest request) {
        long segundos = Math.max(1, ex.getRetryAfter().toSeconds());

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("message", ex.getMessage());
        body.put("retryAfterSeconds", segundos);
        body.put("path", request.getRequestURI());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(segundos))
                .body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN,
                "No tienes los permisos necesarios para acceder a este recurso", request.getRequestURI());
    }

    /**
     * Un valor de la ruta que no encaja con su tipo, tipicamente un id que no
     * es un UUID valido. Sin esto acaba en el manejador generico y sale un 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST,
                "El valor de '" + ex.getName() + "' no tiene el formato esperado",
                request.getRequestURI());
    }

    /**
     * Una ruta que no existe. Sin esto acaba en el manejador generico y sale un 500,
     * que el frontend leeria como un fallo del servidor y no como una ruta mal
     * escrita. Se añadio al retirar {@code /api/medical-certificates/expiring} en el
     * bloque 3c: quien la siga llamando tiene que recibir un 404.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "La ruta no existe", request.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST,
                "El cuerpo de la petición es inválido o está mal formado. Asegúrate de enviar JSON válido",
                request.getRequestURI());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String allowed = String.join(", ", ex.getSupportedMethods() != null ? ex.getSupportedMethods() : new String[]{});
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED,
                "El método " + ex.getMethod() + " no está permitido en esta ruta. Métodos permitidos: " + allowed,
                request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("message", "Errores de validación en los campos enviados");
        body.put("path", request.getRequestURI());
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getClass().getSimpleName() + ": " + ex.getMessage(), request.getRequestURI());
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message, String path) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("message", message);
        body.put("path", path);
        return ResponseEntity.status(status).body(body);
    }
}
