package es.jaes.cn_servicies.training_group;

/**
 * Categoria deportiva del grupo, con la nomenclatura de la RFEN.
 *
 * <p>Los valores van en español y sin tildes porque son nombres propios de la
 * federacion, no vocabulario del dominio: traducirlos a ingles crearia una
 * categoria "ALEVIN" que no se llama asi en ningun sitio. Los nombres de clase
 * si siguen la regla del proyecto.
 *
 * <p>Es la categoria del <b>grupo</b>, no la del atleta. La del atleta sale de
 * su fecha de nacimiento; esta es como el club organiza sus entrenamientos, y
 * un grupo puede mezclar edades a proposito.
 *
 * <p>Añadir un valor es una linea, pero <b>es cambio de contrato</b>: el
 * frontend recibe estos nombres tal cual.
 */
public enum GroupCategory {
    PREBENJAMIN,
    BENJAMIN,
    ALEVIN,
    INFANTIL,
    JUNIOR,
    ABSOLUTO,

    /**
     * Va al final porque el orden de este enum sigue la edad, y master es la de
     * los veteranos: no encaja en la progresion de categorias de formacion.
     */
    MASTER
}
