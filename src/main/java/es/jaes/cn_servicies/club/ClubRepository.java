package es.jaes.cn_servicies.club;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClubRepository extends JpaRepository<Club, UUID> {

    Optional<Club> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
