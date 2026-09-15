package es.jaes.cn_servicies.training_group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TrainingGroupRepository extends JpaRepository<TrainingGroup, UUID> {

    List<TrainingGroup> findBySeasonIdOrderByNameAsc(UUID seasonId);

    List<TrainingGroup> findAllByOrderByNameAsc();

    boolean existsBySeasonIdAndName(UUID seasonId, String name);

    boolean existsBySeasonIdAndNameAndIdNot(UUID seasonId, String name, UUID id);

    /** Para negarse a duplicar sobre una temporada que ya tiene grupos. */
    boolean existsBySeasonId(UUID seasonId);

    /**
     * Los grupos que lleva un usuario, como principal o como ayudante.
     *
     * <p>{@code LEFT JOIN} y no {@code JOIN}: un grupo con principal y sin
     * ayudantes tiene que salir igual. {@code g.coach.id} lo resuelve Hibernate
     * sobre la columna {@code coach_id}, sin join, asi que tampoco se pierden los
     * grupos que solo tienen ayudantes.
     */
    @Query("SELECT DISTINCT g.id FROM TrainingGroup g LEFT JOIN g.assistantCoaches a"
            + " WHERE g.coach.id = :userId OR a.id = :userId")
    List<UUID> findIdsCoachedBy(UUID userId);
}
