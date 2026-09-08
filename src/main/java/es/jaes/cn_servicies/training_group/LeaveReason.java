package es.jaes.cn_servicies.training_group;

/**
 * Por que se cierra una pertenencia.
 *
 * <p><b>Es un enum cerrado y no un texto libre, y eso no es preferencia
 * estetica.</b> Un campo "motivo de baja" en un registro de menores es la via
 * mas corta para que alguien escriba "lo deja por una lesion de rodilla" o algo
 * peor: dato de salud del art. 9 RGPD guardado donde no toca y sin que nadie lo
 * haya decidido. Con valores cerrados eso no puede pasar.
 *
 * <p>Por el mismo motivo <b>no hay un valor de lesion</b>. Si el club necesita
 * distinguir esa causa, es una decision que hay que tomar mirando el RGPD, no
 * añadiendo una constante.
 */
public enum LeaveReason {

    /** Fin de temporada: la baja natural de todo el grupo. */
    END_OF_SEASON,

    /** Cambia de grupo dentro del club. */
    GROUP_CHANGE,

    /** Deja el club. */
    LEFT_CLUB,

    OTHER
}
