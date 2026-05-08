package es.jaes.cn_servicies.athlete_link;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserAthleteRepository extends JpaRepository<UserAthlete, UUID> {

    List<UserAthlete> findByUserId(UUID userId);

    List<UserAthlete> findByAthleteId(UUID athleteId);

    boolean existsByUserIdAndAthleteId(UUID userId, UUID athleteId);
}