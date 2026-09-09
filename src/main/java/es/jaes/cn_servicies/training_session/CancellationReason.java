package es.jaes.cn_servicies.training_session;

/**
 * Por que no se entreno.
 *
 * <p>Enum cerrado y sin nota libre, igual que {@code LeaveReason}. Un campo de
 * texto aqui acaba conteniendo el nombre del nadador que se puso malo, y esto
 * es un registro que ve todo el personal tecnico del club.
 *
 * <p>El precio es {@link #OTHER}, que no cuenta nada. Se asume: si un motivo se
 * repite bajo OTHER, la respuesta es añadir el valor que falta —una linea, y
 * cambio de contrato— y no abrir la puerta al texto libre.
 */
public enum CancellationReason {

    /** Festivo. */
    HOLIDAY,

    /** La piscina no abre: averia, mantenimiento, competicion ajena. */
    POOL_CLOSURE,

    /** Temporal, nieve, alerta meteorologica. */
    WEATHER,

    /** No hay quien lo de. */
    COACH_UNAVAILABLE,

    /** El grupo esta compitiendo ese dia. */
    COMPETITION,

    OTHER
}
