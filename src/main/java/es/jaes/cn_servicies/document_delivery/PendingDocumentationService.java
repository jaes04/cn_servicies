package es.jaes.cn_servicies.document_delivery;

import es.jaes.cn_servicies.athlete.Athlete;
import es.jaes.cn_servicies.athlete.AthleteService;
import es.jaes.cn_servicies.medical_certificate.MedicalCertificateService;
import es.jaes.cn_servicies.medical_certificate.MedicalCertificateStatus;
import es.jaes.cn_servicies.season.Season;
import es.jaes.cn_servicies.season.SeasonService;
import es.jaes.cn_servicies.training_group.AthleteGroupResponse;
import es.jaes.cn_servicies.training_group.AthleteGroupService;
import es.jaes.cn_servicies.training_group.TrainingGroupResponse;
import es.jaes.cn_servicies.training_group.TrainingGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Informe de documentacion pendiente (bloque 3c): quien tiene sin resolver lo que
 * el club pide cada temporada. Certificado medico, solicitud de licencia y
 * documento de identidad.
 *
 * <p>Vive aqui y no en {@code medical_certificate} porque cruza cuatro modulos
 * —grupos, atletas, certificados y entregas— y los usa todos por sus servicios.
 * Nadie depende de este servicio, asi que no cierra ningun ciclo.
 *
 * <p><b>No calcula ningun estado.</b> Se los pide a los servicios de certificados
 * y de entregas, que los calculan con el mismo criterio que la ficha de un atleta.
 * Un informe con reglas propias acabaria diciendo que falta algo que la ficha da
 * por bueno.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PendingDocumentationService {

    private final SeasonService seasonService;
    private final TrainingGroupService groupService;
    private final AthleteGroupService membershipService;
    private final AthleteService athleteService;
    private final MedicalCertificateService certificateService;
    private final DocumentDeliveryService deliveryService;

    /**
     * El informe de la temporada activa.
     *
     * <p><b>Solo cuentan los atletas que hoy estan en algun grupo de la temporada
     * activa.</b> Una ficha sin grupo puede ser de alguien que dejo el club hace
     * dos años, y un aviso lleno de esas fichas deja de leerse. El precio es que en
     * septiembre hay que meter a cada nadador en su grupo antes de que aparezca.
     *
     * <p>Los miembros se sacan con la consulta de pertenencia de siempre, grupo a
     * grupo, y no con una consulta propia: el criterio de quien esta en un grupo en
     * una fecha ya esta escrito en demasiados sitios.
     *
     * @param onlyGroups los grupos a los que se acota; {@code null} no acota. Es lo
     *                   que ve un entrenador: solo sus grupos
     */
    public PendingDocumentationResponse pending(Collection<UUID> onlyGroups) {
        PendingDocumentationResponse response = new PendingDocumentationResponse();

        Optional<Season> activa = seasonService.findActiveSeason();
        if (activa.isEmpty()) {
            return response;
        }
        response.setSeasonId(activa.get().getId());
        response.setSeasonName(activa.get().getName());

        LocalDate hoy = LocalDate.now();
        Map<UUID, PendingAthleteDocumentationResponse> porAtleta = new LinkedHashMap<>();
        for (TrainingGroupResponse grupo : groupService.findAll(activa.get().getId(), onlyGroups)) {
            for (AthleteGroupResponse miembro : membershipService.membersOnAsResponse(grupo.getId(), hoy)) {
                PendingAthleteDocumentationResponse entrada = porAtleta.computeIfAbsent(
                        miembro.getAthleteId(), id -> nuevaEntrada(id, miembro.getAthleteName()));
                entrada.getGroups().add(grupo.getName());
            }
        }
        if (porAtleta.isEmpty()) {
            return response;
        }

        Map<UUID, MedicalCertificateStatus> certificados =
                certificateService.statusesForAthletes(porAtleta.keySet());
        List<Athlete> fichas = athleteService.findAllByIds(porAtleta.keySet());
        Map<UUID, Map<DocumentDeliveryType, DocumentDeliveryStatus>> papeles =
                deliveryService.statusesForAthletes(fichas);

        List<PendingAthleteDocumentationResponse> pendientes = new ArrayList<>();
        for (PendingAthleteDocumentationResponse entrada : porAtleta.values()) {
            Map<DocumentDeliveryType, DocumentDeliveryStatus> suyos = papeles.get(entrada.getAthleteId());
            if (suyos == null) {
                // La ficha no ha vuelto de la consulta: borrada entre medias. No es de nadie a quien avisar.
                continue;
            }
            entrada.setMedicalCertificate(certificados.get(entrada.getAthleteId()));
            entrada.setLicenseApplication(suyos.get(DocumentDeliveryType.LICENSE_APPLICATION));
            entrada.setIdentityDocument(suyos.get(DocumentDeliveryType.IDENTITY_DOCUMENT));
            if (tieneAlgoPendiente(entrada)) {
                pendientes.add(entrada);
            }
        }

        pendientes.sort(Comparator.comparing(PendingAthleteDocumentationResponse::getAthleteName,
                String.CASE_INSENSITIVE_ORDER));
        response.setAthletes(pendientes);
        response.setTotal(pendientes.size());
        return response;
    }

    /**
     * Algo pendiente es cualquier papel que no este en regla. <b>A punto de caducar
     * cuenta</b>: es justo cuando conviene pedirlo. {@code NOT_REQUIRED} no cuenta:
     * no hay nada que pedir.
     */
    static boolean tieneAlgoPendiente(PendingAthleteDocumentationResponse entrada) {
        return entrada.getMedicalCertificate() != MedicalCertificateStatus.VALID
                || pendiente(entrada.getLicenseApplication())
                || pendiente(entrada.getIdentityDocument());
    }

    private static boolean pendiente(DocumentDeliveryStatus estado) {
        return estado != DocumentDeliveryStatus.VALID && estado != DocumentDeliveryStatus.NOT_REQUIRED;
    }

    private static PendingAthleteDocumentationResponse nuevaEntrada(UUID athleteId, String nombre) {
        PendingAthleteDocumentationResponse entrada = new PendingAthleteDocumentationResponse();
        entrada.setAthleteId(athleteId);
        entrada.setAthleteName(nombre);
        return entrada;
    }
}
