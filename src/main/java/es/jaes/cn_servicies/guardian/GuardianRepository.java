package es.jaes.cn_servicies.guardian;

import es.jaes.cn_servicies.club.Club;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface GuardianRepository extends JpaRepository<Guardian, UUID>,
        JpaSpecificationExecutor<Guardian> {

    /**
     * Acotada al club a proposito: el dni del tutor es unico por club, no
     * global. Mismo criterio que en atletas.
     */
    Optional<Guardian> findByClubAndDni(Club club, String dni);

    boolean existsByClubAndDni(Club club, String dni);

    Optional<Guardian> findByUserId(UUID userId);
}
