package es.jaes.cn_servicies.auth;

/**
 * Para que sirve un token. Viaja en el claim {@code typ} y se comprueba en cada
 * uso.
 *
 * <p>Hasta ahora los dos tokens eran indistinguibles salvo por la caducidad, y
 * eso hacia que el de refresco —que dura una semana— valiera tambien como token
 * de acceso, y que el de acceso valiera para pedir uno nuevo indefinidamente.
 * Lo segundo convierte la caducidad corta del acceso en decorativa: quien roba
 * uno se renueva solo.
 *
 * <p>Un token sin este claim se rechaza. Son los emitidos antes de este cambio,
 * y no hay forma de saber para que se emitieron.
 */
public enum TokenType {

    /** Autentica una peticion. Corto. */
    ACCESS,

    /** Solo sirve para pedir un par nuevo en {@code /api/auth/refresh}. Largo. */
    REFRESH
}
