package es.jaes.cn_servicies.training_session;

import lombok.Data;

import java.time.LocalDate;

/**
 * Resumen de una generacion.
 *
 * <p>Devuelve el conteo y no la lista de sesiones a proposito: seis semanas de
 * cuatro grupos son cien filas que nadie va a leer, y lo que hay que saber es
 * cuantas se crearon.
 *
 * <p>{@link #alreadyExisted} no es ruido: es <b>la prueba de la idempotencia</b>.
 * Lanzar la generacion dos veces sobre el mismo rango tiene que dar cero creadas
 * y el mismo numero que creo la primera vez, y eso se ve aqui sin mirar la base.
 */
@Data
public class SessionGenerationResponse {

    private LocalDate from;
    private LocalDate to;

    /** Sesiones nuevas. */
    private int created;

    /** Las que ya estaban y por tanto no se han tocado. */
    private int alreadyExisted;

    /**
     * Cuantas de las creadas nacieron ya canceladas por caer en un cierre.
     *
     * <p>Van incluidas en {@link #created}: se han creado, solo que canceladas.
     * Separarlas es lo que permite ver de un vistazo que un rango entero salio
     * en festivo, que casi siempre significa que las fechas del cierre estan
     * mal.
     */
    private int bornCancelled;
}
