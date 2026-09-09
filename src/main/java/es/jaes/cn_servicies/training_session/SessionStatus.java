package es.jaes.cn_servicies.training_session;

/**
 * Estado de una sesion.
 *
 * <p>Nace {@link #SCHEDULED} y de ahi solo sale por dos caminos: se cancela, o
 * se pasa lista y queda {@link #DONE}. El paso a DONE lo hace la 2.3 al guardar
 * la asistencia, no un boton aparte: una sesion con lista pasada esta hecha por
 * definicion, y dejar los dos estados a mano garantiza que se desincronicen.
 */
public enum SessionStatus {

    /** Generada o creada a mano, todavia sin ocurrir. */
    SCHEDULED,

    /** Se paso lista. Lo pone la 2.3. */
    DONE,

    /** No se entreno. Lleva siempre un motivo. */
    CANCELLED
}
