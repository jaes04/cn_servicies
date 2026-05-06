package es.jaes.cn_servicies.competition_result;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface CompetitionResultRepository extends JpaRepository<CompetitionResult, UUID>, JpaSpecificationExecutor<CompetitionResult> {
    List<CompetitionResult> findByAthleteId(UUID athleteId);
}