package es.jaes.cn_servicies.guardian;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AthleteGuardianRepository extends JpaRepository<AthleteGuardian, UUID> {

    List<AthleteGuardian> findByAthleteId(UUID athleteId);

    List<AthleteGuardian> findByGuardianId(UUID guardianId);

    boolean existsByAthleteIdAndGuardianId(UUID athleteId, UUID guardianId);
}
