package es.jaes.cn_servicies.training_session;

/**
 * Como asistio un atleta a una sesion.
 *
 * <p><b>Enum cerrado y sin campo de notas al lado</b>, y esa ausencia es la
 * decision de diseño de esta tarea. El roadmap pedia un campo de observaciones;
 * se quito. Un texto libre en el registro de asistencia de un menor —que ve todo
 * el personal tecnico del club— acaba conteniendo "no vino, esta con
 * gastroenteritis": dato de salud, categoria especial del art. 9 del RGPD,
 * guardado sin base legal y sin que el tutor lo sepa.
 *
 * <p>Es el mismo criterio que ya se aplico a {@code LeaveReason} y a
 * {@link CancellationReason}. Ver {@code docs/rgpd.md} §3 y §9.
 *
 * <p><b>{@link #EXCUSED} no guarda el motivo</b>, y no es un olvido: justificar
 * una falta es una conversacion con el tutor, no una columna. Lo unico que el
 * sistema necesita saber es si esa falta cuenta en el porcentaje de asistencia.
 */
public enum AttendanceStatus {

    /** Vino. */
    PRESENT,

    /** No vino, y la falta no esta justificada. */
    ABSENT,

    /** No vino, pero el club lo daba por justificado. Sin registrar por que. */
    EXCUSED,

    /** Vino tarde. Se cuenta como asistencia, pero el club quiere verlo. */
    LATE
}
