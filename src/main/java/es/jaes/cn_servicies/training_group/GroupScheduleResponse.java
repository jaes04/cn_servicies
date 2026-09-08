package es.jaes.cn_servicies.training_group;

import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class GroupScheduleResponse {

    private UUID id;
    private UUID groupId;
    private String groupName;
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private TrainingModality modality;
    private LocalDate validFrom;
    private LocalDate validUntil;

    /**
     * Si esta en vigor hoy. Va calculado para que el cliente no tenga que
     * repetir la comparacion de fechas —y no se equivoque en el extremo, que es
     * donde se falla.
     */
    private boolean inForce;
}
