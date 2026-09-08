package es.jaes.cn_servicies.training_group;

/**
 * Nivel tecnico del grupo.
 *
 * <p>A diferencia de {@link GroupCategory}, este vocabulario <b>no lo fija
 * ninguna federacion</b>: es como organiza el club. Con un segundo cliente es
 * probable que no le sirva, y ese sera el momento de plantear si pasa a ser un
 * catalogo por club en vez de un enum en codigo.
 */
public enum GroupLevel {
    INICIACION,
    PERFECCIONAMIENTO,
    COMPETICION
}
