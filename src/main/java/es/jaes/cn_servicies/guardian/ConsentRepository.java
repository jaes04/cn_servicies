package es.jaes.cn_servicies.guardian;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConsentRepository extends JpaRepository<Consent, UUID> {

    List<Consent> findByAthleteIdOrderByDecisionDateDesc(UUID athleteId);

    List<Consent> findByAthleteIdAndType(UUID athleteId, ConsentType type);

    /**
     * Vigente = otorgado y sin revocar. La consulta se escribe sobre el estado,
     * no sobre "la ultima fila": el historial puede tener una concesion, una
     * revocacion y otra concesion posterior, y quedarse con la mas reciente por
     * fecha daria la respuesta equivocada si dos comparten dia.
     */
    boolean existsByAthleteIdAndTypeAndGrantedTrueAndRevokedAtIsNull(UUID athleteId, ConsentType type);

    List<Consent> findByGuardianId(UUID guardianId);
}
