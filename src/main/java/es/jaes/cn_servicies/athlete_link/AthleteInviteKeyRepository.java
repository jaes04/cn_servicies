package es.jaes.cn_servicies.athlete_link;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AthleteInviteKeyRepository extends JpaRepository<AthleteInviteKey, UUID> {

    Optional<AthleteInviteKey> findByKeyValue(String keyValue);
}