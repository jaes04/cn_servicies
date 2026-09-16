package es.jaes.cn_servicies.auth;

/**
 * El token que llega en el cuerpo no sirve para lo que se esta pidiendo: no es
 * valido, ha caducado o es de otro tipo.
 *
 * <p>Sale como 401 y no como 400: el cliente no tiene que corregir el cuerpo de
 * la peticion, tiene que volver a identificarse. Es la misma respuesta que da
 * una peticion sin token, para que la pantalla trate los dos casos igual.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
