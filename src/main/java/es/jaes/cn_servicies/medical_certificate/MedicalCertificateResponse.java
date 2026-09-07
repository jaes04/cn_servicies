package es.jaes.cn_servicies.medical_certificate;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class MedicalCertificateResponse {

    private UUID id;
    private UUID athleteId;
    private LocalDate issuedOn;
    private LocalDate expiresOn;

    /** Calculado a dia de hoy. El cliente no tiene que deducirlo de las fechas. */
    private MedicalCertificateStatus status;

    private String validatedBy;
    private LocalDateTime validatedAt;
}
