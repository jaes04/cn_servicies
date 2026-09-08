package es.jaes.cn_servicies.training_group;

import lombok.Data;

import java.util.UUID;

@Data
public class TrainingGroupResponse {

    private UUID id;
    private UUID seasonId;
    private String seasonName;
    private String name;
    private GroupCategory category;
    private GroupLevel level;
    private Integer maxSlots;

    /** Nulos los dos mientras el grupo no tenga entrenador asignado. */
    private UUID coachId;
    private String coachUsername;
}
