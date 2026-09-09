package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.training_group.TrainingModality;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Todo lo que hace falta para pasar lista, en una sola llamada.
 *
 * <p>Trae los datos de la sesion <b>y</b> los atletas con su estado, a proposito:
 * es el endpoint que consumira el movil, muchas veces con mala cobertura al
 * borde de una piscina. Dos llamadas encadenadas ahi son dos oportunidades de
 * quedarse a medias.
 *
 * <p>Los atletas son <b>los que pertenecian al grupo el dia de la sesion</b>, no
 * los de hoy. Pasar lista de un entrenamiento de hace tres semanas tiene que
 * mostrar a quien estaba entonces.
 */
@Data
public class RosterResponse {

    private UUID sessionId;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private TrainingModality modality;
    private SessionStatus status;

    private UUID groupId;
    private String groupName;

    private List<RosterEntryResponse> athletes;
}
