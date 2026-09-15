package es.jaes.cn_servicies.document_delivery;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Entrega de un papel. Que campos lleva depende del tipo, y lo comprueba el
 * servicio:
 * <ul>
 *   <li>Licencia: {@code seasonId}, sin fechas.</li>
 *   <li>Documento de identidad: {@code validUntil}, su caducidad.</li>
 *   <li>Permiso de viaje: {@code validFrom} y {@code validUntil}, salida y vuelta.</li>
 * </ul>
 *
 * <p>Si alguna vez alguien propone anadir aqui un campo de notas, la respuesta
 * esta en el javadoc de {@link DocumentDelivery}.
 */
@Data
public class DocumentDeliveryRequest {

    @NotNull
    private DocumentDeliveryType type;

    @NotNull
    @PastOrPresent(message = "La fecha de entrega no puede ser futura")
    private LocalDate deliveredOn;

    private UUID seasonId;

    private LocalDate validFrom;

    /** Puede ser pasada: registrar un documento ya caducado es legitimo, y el estado lo dira. */
    private LocalDate validUntil;
}
