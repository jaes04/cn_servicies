package es.jaes.cn_servicies.training_group;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * El grupo no viene en el cuerpo: va en la ruta
 * ({@code /api/groups/{id}/schedules}), que es lo que permite comprobar la
 * pertenencia contra un grupo que si esta bajo RLS.
 */
@Data
public class GroupScheduleRequest {

    @NotNull
    private DayOfWeek dayOfWeek;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    @NotNull
    private TrainingModality modality;

    @NotNull
    private LocalDate validFrom;

    /** Opcional: sin valor, el horario sigue vigente hasta que se cierre o se borre. */
    private LocalDate validUntil;
}
