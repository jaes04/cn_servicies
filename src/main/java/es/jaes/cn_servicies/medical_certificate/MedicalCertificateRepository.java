package es.jaes.cn_servicies.medical_certificate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MedicalCertificateRepository extends JpaRepository<MedicalCertificate, UUID> {

    List<MedicalCertificate> findByAthleteIdOrderByExpiresOnDesc(UUID athleteId);

    /**
     * Los de varios atletas de una vez. Lo usa el informe de documentacion
     * pendiente (bloque 3c): de uno en uno serian dos consultas por atleta.
     */
    List<MedicalCertificate> findByAthleteIdIn(Collection<UUID> athleteIds);
}
