package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.training_group.TrainingModality;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class ClubClosureResponse {

    private UUID id;
    private LocalDate startDate;
    private LocalDate endDate;
    private CancellationReason reason;

    /** Nulo: afecta a todas las modalidades. */
    private TrainingModality modality;

    /**
     * Cuantas sesiones ya generadas ha cancelado el alta de este cierre.
     *
     * <p>Solo viene relleno en la respuesta del POST, y no es adorno: declarar
     * un cierre con las fechas mal puede tumbar veinte entrenamientos, y quien
     * lo hace tiene que enterarse en ese momento y no en marzo.
     */
    private Integer cancelledSessions;
}
