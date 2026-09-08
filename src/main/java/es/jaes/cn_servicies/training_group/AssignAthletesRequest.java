package es.jaes.cn_servicies.training_group;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Alta de atletas en un grupo, uno o muchos.
 *
 * <p>Una sola forma para los dos casos: dar de alta a uno es una lista de uno.
 * Dos endpoints para lo mismo solo consiguen que uno de los dos se quede sin
 * mantener.
 *
 * <p><b>Se aplica entera o no se aplica.</b> Si un atleta de la lista ya está
 * en el grupo, no se da de alta a ninguno: media asignación deja al club sin
 * saber quién entró y quién no.
 */
@Data
public class AssignAthletesRequest {

    @NotEmpty(message = "Hay que indicar al menos un atleta")
    private List<UUID> athleteIds;

    @NotNull
    private LocalDate joinedOn;

    /**
     * Opcional, para mover de grupo: cierra la pertenencia de ese otro grupo en
     * la misma operación, con motivo {@code GROUP_CHANGE}.
     */
    private UUID replacesGroupId;
}
