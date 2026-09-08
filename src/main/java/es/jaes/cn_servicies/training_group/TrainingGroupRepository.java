package es.jaes.cn_servicies.training_group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TrainingGroupRepository extends JpaRepository<TrainingGroup, UUID> {

    List<TrainingGroup> findBySeasonIdOrderByNameAsc(UUID seasonId);

    List<TrainingGroup> findAllByOrderByNameAsc();

    boolean existsBySeasonIdAndName(UUID seasonId, String name);

    boolean existsBySeasonIdAndNameAndIdNot(UUID seasonId, String name, UUID id);

    /** Para negarse a duplicar sobre una temporada que ya tiene grupos. */
    boolean existsBySeasonId(UUID seasonId);
}
