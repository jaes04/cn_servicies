package es.jaes.cn_servicies.training_group;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Una pertenencia.
 *
 * <p>Lleva el nombre del atleta porque es lo que hay que ver en la lista de un
 * grupo, pero <b>ni su DNI ni su fecha de nacimiento</b>: para pasar lista no
 * hacen falta, y en un listado no pintan nada.
 */
@Data
public class AthleteGroupResponse {

    private UUID id;
    private UUID athleteId;
    private String athleteName;
    private UUID groupId;
    private String groupName;
    private LocalDate joinedOn;
    private LocalDate leftOn;
    private LeaveReason leaveReason;

    /** Sin fecha de baja. Va calculado para que el cliente no tenga que deducirlo. */
    private boolean open;
}
