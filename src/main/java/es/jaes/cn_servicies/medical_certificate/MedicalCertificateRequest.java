package es.jaes.cn_servicies.medical_certificate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lo unico que se pide de un certificado: cuando se emitio y para que temporada.
 *
 * <p><b>Desde el bloque 3b no se pide la caducidad.</b> El club pide un
 * certificado por temporada y ese es su plazo de validez, asi que la caducidad es
 * el ultimo dia de la temporada y la pone el servicio. Pedirla aqui dejaria
 * teclear una fecha que no significa nada. Es cambio de contrato: la peticion
 * lleva {@code seasonId} en lugar de {@code expiresOn}.
 *
 * <p>Si alguna vez alguien propone anadir aqui un campo de observaciones, la
 * respuesta esta en el javadoc de {@link MedicalCertificate}.
 */
@Data
public class MedicalCertificateRequest {

    @NotNull
    @PastOrPresent(message = "La fecha de emisión no puede ser futura")
    private LocalDate issuedOn;

    /**
     * La temporada que cubre. <b>Explicita, y no la activa por defecto</b>: a
     * finales de agosto es muy facil registrar el certificado del curso que
     * empieza en la temporada que acaba, sin darse cuenta.
     */
    @NotNull
    private UUID seasonId;
}
