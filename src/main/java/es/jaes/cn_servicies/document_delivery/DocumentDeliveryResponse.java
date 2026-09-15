package es.jaes.cn_servicies.document_delivery;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class DocumentDeliveryResponse {

    private UUID id;
    private UUID athleteId;
    private DocumentDeliveryType type;

    /** Solo en la licencia. */
    private UUID seasonId;
    private String seasonName;

    /** Solo en el permiso de viaje. */
    private LocalDate validFrom;

    /** En el documento de identidad y en el permiso de viaje. */
    private LocalDate validUntil;

    private LocalDate deliveredOn;

    /** Calculado a dia de hoy. El cliente no tiene que deducirlo de las fechas. */
    private DocumentDeliveryStatus status;

    private String registeredBy;
    private LocalDateTime registeredAt;
}
