package es.jaes.cn_servicies.document_delivery;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Si un atleta necesita permiso para un viaje, y si lo tiene.
 *
 * <p>Son dos preguntas y van separadas a proposito: {@code covered} dice
 * literalmente si hay un permiso que cubra las fechas, tambien cuando no hace
 * falta. Juntarlas en un "puede viajar" esconderia por que.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TravelPermitCoverageResponse {

    private LocalDate from;
    private LocalDate to;

    /** Si el atleta es menor de edad el dia de salida. */
    private boolean required;

    /** Si hay un permiso registrado que cubre del dia de salida al de vuelta, ambos incluidos. */
    private boolean covered;
}
