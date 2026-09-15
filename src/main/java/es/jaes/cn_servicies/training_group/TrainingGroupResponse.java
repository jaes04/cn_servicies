package es.jaes.cn_servicies.training_group;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * Atletas con pertenencia abierta. Junto con maxSlots es lo que deja a la
     * interfaz avisar de que un grupo esta lleno: <b>el alta no se bloquea por
     * plazas</b>, decision pendiente.
     */
    private long memberCount;

    /** Nulos los dos mientras el grupo no tenga entrenador principal asignado. */
    private UUID coachId;
    private String coachUsername;

    /** Ordenados por nombre de usuario. Vacia, nunca nula, si no hay ayudantes. */
    private List<GroupCoachResponse> assistantCoaches = new ArrayList<>();
}
