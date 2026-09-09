package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.training_group.TrainingModality;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class TrainingSessionResponse {

    private UUID id;
    private UUID groupId;
    private String groupName;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private TrainingModality modality;
    private SessionStatus status;

    /** Solo con {@code status = CANCELLED}. */
    private CancellationReason cancellationReason;

    /**
     * Sin horario detras: creada a mano. Va calculado porque la interfaz la
     * pinta distinta —una competicion no es el entrenamiento de los martes— y
     * el id del horario no le sirve para nada.
     */
    private boolean oneOff;
}
