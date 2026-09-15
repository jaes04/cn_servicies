package es.jaes.cn_servicies.training_group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class TrainingGroupRequest {

    @NotNull
    private UUID seasonId;

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    private GroupCategory category;

    @NotNull
    private GroupLevel level;

    /** Opcional: sin valor, el grupo no tiene limite de plazas. */
    @Positive(message = "Las plazas máximas tienen que ser un número positivo")
    private Integer maxSlots;

    /** Opcional: un grupo puede existir antes de saber quién lo lleva. */
    private UUID coachId;

    /**
     * Opcional. Como el resto de campos del {@code PUT}, <b>sustituye</b> a los
     * que hubiera: sin valor, el grupo se queda sin ayudantes.
     */
    private Set<UUID> assistantCoachIds;
}
