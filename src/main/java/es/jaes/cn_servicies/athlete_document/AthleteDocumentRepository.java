package es.jaes.cn_servicies.athlete_document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AthleteDocumentRepository extends JpaRepository<AthleteDocument, UUID> {

    List<AthleteDocument> findByAthleteId(UUID athleteId);

    List<AthleteDocument> findByAthleteIdIn(List<UUID> athleteIds);
}