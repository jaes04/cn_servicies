package es.jaes.cn_servicies.training_session;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/** Lo que se registra de un atleta: quien es y como asistio. Nada mas. */
@Data
public class AttendanceEntryRequest {

    @NotNull
    private UUID athleteId;

    @NotNull
    private AttendanceStatus status;
}
