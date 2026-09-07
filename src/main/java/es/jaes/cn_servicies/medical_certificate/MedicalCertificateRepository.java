package es.jaes.cn_servicies.medical_certificate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicalCertificateRepository extends JpaRepository<MedicalCertificate, UUID> {

    List<MedicalCertificate> findByAthleteIdOrderByExpiresOnDesc(UUID athleteId);

    /**
     * El que mas lejos caduca, que es el que manda. No sirve "el ultimo
     * registrado": si alguien teclea hoy el certificado del ano pasado, ese es
     * el mas reciente por fecha de alta y no es el vigente.
     */
    Optional<MedicalCertificate> findFirstByAthleteIdOrderByExpiresOnDesc(UUID athleteId);

    /**
     * Los que caducan entre dos fechas, para el panel de avisos. Acotado por
     * arriba y por abajo: los ya caducados no son un aviso, son otra lista.
     */
    List<MedicalCertificate> findByExpiresOnBetweenOrderByExpiresOnAsc(LocalDate desde, LocalDate hasta);
}
