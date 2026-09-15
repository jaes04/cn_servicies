package es.jaes.cn_servicies.medical_certificate;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class MedicalCertificateResponse {

    private UUID id;
    private UUID athleteId;

    /** La temporada que cubre. Nulos solo en un certificado anterior al bloque 3b que no se pudo asignar. */
    private UUID seasonId;
    private String seasonName;

    private LocalDate issuedOn;

    /** Ultimo dia en que cubre: el final de su temporada. */
    private LocalDate expiresOn;

    /** Calculado a dia de hoy. El cliente no tiene que deducirlo de las fechas. */
    private MedicalCertificateStatus status;

    private String validatedBy;
    private LocalDateTime validatedAt;
}
