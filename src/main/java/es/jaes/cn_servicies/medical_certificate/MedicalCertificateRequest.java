package es.jaes.cn_servicies.medical_certificate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDate;

/**
 * Lo unico que se pide de un certificado: cuando se emitio y hasta cuando vale.
 *
 * <p>Si alguna vez alguien propone anadir aqui un campo de observaciones, la
 * respuesta esta en el javadoc de {@link MedicalCertificate}.
 */
@Data
public class MedicalCertificateRequest {

    @NotNull
    @PastOrPresent(message = "La fecha de emisión no puede ser futura")
    private LocalDate issuedOn;

    /** Puede ser pasada: registrar uno ya caducado es legitimo, y el estado lo dirá. */
    @NotNull
    private LocalDate expiresOn;
}
