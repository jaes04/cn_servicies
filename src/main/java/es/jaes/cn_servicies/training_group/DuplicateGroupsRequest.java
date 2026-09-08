package es.jaes.cn_servicies.training_group;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Copiar los grupos de una temporada a otra.
 *
 * <p>Parece secundario hasta el septiembre en que el club tiene que recrear
 * catorce grupos a mano.
 */
@Data
public class DuplicateGroupsRequest {

    @NotNull
    private UUID fromSeasonId;

    @NotNull
    private UUID toSeasonId;
}
