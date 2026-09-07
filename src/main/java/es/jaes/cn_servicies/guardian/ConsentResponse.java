package es.jaes.cn_servicies.guardian;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Una decision del historial de consentimientos.
 *
 * <p><b>No lleva la IP de origen.</b> Es dato personal cuya finalidad es
 * acreditar el consentimiento si alguien lo discute, no alimentar una pantalla:
 * quien la necesite, la consulta en la base. Tampoco lleva el DNI ni el correo
 * del tutor; su nombre basta para saber quien consintio.
 */
@Data
public class ConsentResponse {

    private UUID id;
    private ConsentType type;
    private boolean granted;
    private LocalDate decisionDate;
    private ConsentEvidenceType evidenceType;
    private String evidenceRef;
    private LocalDateTime revokedAt;

    /** Otorgado y sin revocar. Va calculado para que el cliente no tenga que deducirlo. */
    private boolean active;

    private UUID guardianId;
    private String guardianName;
}
