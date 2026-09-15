package es.jaes.cn_servicies.document_delivery;

import es.jaes.cn_servicies.medical_certificate.MedicalCertificateStatus;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Un atleta con algo pendiente, y el estado de cada papel.
 *
 * <p>Lleva nombre, grupos y estados, y nada mas: ni documento de identidad ni
 * fecha de nacimiento. Es un aviso que ven entrenadores, no una ficha.
 */
@Data
public class PendingAthleteDocumentationResponse {

    private UUID athleteId;
    private String athleteName;

    /** Grupos de la temporada activa en los que esta hoy. Para un entrenador, solo los suyos. */
    private List<String> groups = new ArrayList<>();

    private MedicalCertificateStatus medicalCertificate;
    private DocumentDeliveryStatus licenseApplication;
    private DocumentDeliveryStatus identityDocument;
}
