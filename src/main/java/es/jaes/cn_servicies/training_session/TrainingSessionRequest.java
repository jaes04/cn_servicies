package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.training_group.TrainingModality;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Alta de una sesion puntual: competicion, entrenamiento extra, recuperacion.
 *
 * <p>No lleva {@code scheduleId}: una sesion que sale de un horario la crea el
 * generador, no una peticion. Poder elegir el horario a mano abriria la puerta a
 * meter a mano el duplicado que la 2.2 entera existe para evitar.
 *
 * <p>Tampoco lleva {@code status}: una sesion nace SCHEDULED. Cancelar es su
 * endpoint.
 */
@Data
public class TrainingSessionRequest {

    @NotNull
    private LocalDate date;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    @NotNull
    private TrainingModality modality;
}
