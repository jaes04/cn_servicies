package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.training_group.TrainingModality;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ClubClosureRequest {

    @NotNull
    private LocalDate startDate;

    /** Ultimo dia cerrado, incluido. En un festivo suelto, el mismo que el de inicio. */
    @NotNull
    private LocalDate endDate;

    @NotNull
    private CancellationReason reason;

    /**
     * Opcional. Sin valor el cierre afecta a todo; con {@code SWIMMING} solo
     * tumba lo del agua y deja en pie el entrenamiento en seco.
     */
    private TrainingModality modality;
}
