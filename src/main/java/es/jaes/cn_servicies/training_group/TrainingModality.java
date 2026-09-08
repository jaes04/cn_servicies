package es.jaes.cn_servicies.training_group;

/**
 * En que se entrena: dentro del agua o fuera.
 *
 * <p>Va en el <b>horario</b> y no en el grupo a proposito. Un mismo grupo hace
 * agua el martes y seco el jueves, que es el caso corriente: ponerlo en el
 * grupo obligaria a partir "Alevin A" en dos grupos que comparten los mismos
 * nadadores, y a duplicar su composicion y su historico.
 *
 * <p>Enum cerrado, como {@link GroupCategory} y {@link GroupLevel}: aqui no hay
 * texto libre. Añadir un valor es una linea, pero <b>es cambio de contrato</b>.
 */
public enum TrainingModality {

    /** En el agua. */
    SWIMMING,

    /** En seco: preparacion fisica, gimnasio, sala. */
    DRYLAND
}
