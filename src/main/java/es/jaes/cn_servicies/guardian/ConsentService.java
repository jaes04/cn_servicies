package es.jaes.cn_servicies.guardian;

import es.jaes.cn_servicies.athlete.Athlete;
import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.club.ClubService;
import es.jaes.cn_servicies.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ConsentService {

    /**
     * Edad a partir de la cual el menor consiente por si mismo el tratamiento de
     * sus datos en Espana: <b>14 anos</b> (LOPDGDD art. 7). No son 16, que es lo
     * que fija el RGPD como maximo y lo que suele asumirse por defecto. Por
     * debajo de esa edad consiente quien ejerce la patria potestad o la tutela.
     */
    public static final int CONSENT_AGE = 14;

    private final ConsentRepository consentRepository;
    private final GuardianRepository guardianRepository;
    private final AthleteGuardianRepository athleteGuardianRepository;
    private final ClubService clubService;

    /**
     * Registra una decision del tutor, sea concesion o negativa. Nunca modifica
     * una fila anterior: cada decision es una fila nueva.
     *
     * <p><b>Recibe el atleta ya resuelto, no su id.</b> Buscarlo aqui obligaria
     * a depender de {@code AthleteService}, que a su vez llama a este servicio
     * en el alta: seria un ciclo. Es la misma solucion que en la 0.8 con el
     * club, y ademas es mejor reparto: quien registra un consentimiento sabe
     * sobre que atleta lo hace.
     *
     * @param sourceIp IP de quien envia el formulario. Solo se conserva cuando
     *                 la evidencia es {@code ONLINE_FORM}; en los demas casos se
     *                 descarta, porque no prueba nada y es dato personal.
     */
    public Consent record(Athlete athlete, ConsentRequest request, String sourceIp) {
        Club club = clubService.getById(TenantContext.require());
        Guardian guardian = guardianRepository.findById(request.getGuardianId())
                .orElseThrow(() -> new EntityNotFoundException("Tutor no encontrado"));

        // Sin vinculo, cualquier tutor del club podria consentir por cualquier
        // menor del club. El vinculo es lo que acota quien puede hacerlo.
        if (!athleteGuardianRepository.existsByAthleteIdAndGuardianId(athlete.getId(), guardian.getId())) {
            throw new IllegalArgumentException("El tutor no está vinculado a este atleta");
        }

        Consent consent = new Consent();
        consent.setClub(club);
        consent.setAthlete(athlete);
        consent.setGuardian(guardian);
        consent.setType(request.getType());
        consent.setGranted(Boolean.TRUE.equals(request.getGranted()));
        consent.setDecisionDate(request.getDecisionDate());
        consent.setEvidenceType(request.getEvidenceType());
        consent.setEvidenceRef(request.getEvidenceRef());
        consent.setSourceIp(
                request.getEvidenceType() == ConsentEvidenceType.ONLINE_FORM ? sourceIp : null);

        return consentRepository.save(consent);
    }

    /**
     * Revoca por fecha, nunca borrando la fila: la prueba de que el
     * consentimiento existio tiene que sobrevivir a su retirada.
     */
    public Consent revoke(UUID consentId) {
        Consent consent = consentRepository.findById(consentId)
                .orElseThrow(() -> new EntityNotFoundException("Consentimiento no encontrado"));

        if (!consent.isGranted()) {
            throw new IllegalArgumentException("Una negativa no se revoca");
        }
        if (consent.getRevokedAt() != null) {
            throw new IllegalArgumentException("El consentimiento ya estaba revocado");
        }

        consent.setRevokedAt(LocalDateTime.now());
        return consentRepository.save(consent);
    }

    @Transactional(readOnly = true)
    public List<Consent> findByAthlete(UUID athleteId) {
        return consentRepository.findByAthleteIdOrderByDecisionDateDesc(athleteId);
    }

    /** Historial completo, con la evidencia. Es la vista del administrador. */
    @Transactional(readOnly = true)
    public List<ConsentResponse> historyForAthlete(UUID athleteId) {
        return consentRepository.findByAthleteIdOrderByDecisionDateDesc(athleteId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Que se puede hacer hoy con este atleta, una linea por finalidad. Es la
     * vista del entrenador: para saber si un nino puede salir en una foto no
     * hace falta saber quien firmo, cuando, ni con que papel.
     *
     * <p>Devuelve todas las finalidades, tambien las que nadie ha contestado
     * nunca: ausencia es {@code false}, que es lo que corresponde. Un
     * consentimiento que no consta no ampara nada.
     */
    @Transactional(readOnly = true)
    public Map<ConsentType, Boolean> statusForAthlete(UUID athleteId) {
        Map<ConsentType, Boolean> status = new EnumMap<>(ConsentType.class);
        for (ConsentType type : ConsentType.values()) {
            status.put(type, hasActiveConsent(athleteId, type));
        }
        return status;
    }

    public ConsentResponse toResponse(Consent consent) {
        ConsentResponse response = new ConsentResponse();
        response.setId(consent.getId());
        response.setType(consent.getType());
        response.setGranted(consent.isGranted());
        response.setDecisionDate(consent.getDecisionDate());
        response.setEvidenceType(consent.getEvidenceType());
        response.setEvidenceRef(consent.getEvidenceRef());
        response.setRevokedAt(consent.getRevokedAt());
        response.setActive(consent.isActive());
        response.setGuardianId(consent.getGuardian().getId());
        response.setGuardianName(
                consent.getGuardian().getFirstName() + " " + consent.getGuardian().getLastName());
        return response;
    }

    /** Si hay consentimiento vigente para esa finalidad concreta. */
    @Transactional(readOnly = true)
    public boolean hasActiveConsent(UUID athleteId, ConsentType type) {
        return consentRepository
                .existsByAthleteIdAndTypeAndGrantedTrueAndRevokedAtIsNull(athleteId, type);
    }

    /**
     * Si el atleta necesita <b>hoy</b> que consienta su tutor. Es la pregunta
     * para dar de alta o para pedir una autorizacion nueva.
     */
    public static boolean requiresGuardianConsent(Athlete athlete) {
        return isUnderConsentAgeOn(athlete.getBirthDate(), LocalDate.now());
    }

    /**
     * Si el atleta era menor de 14 <b>en la fecha en que se firmo</b> ese
     * consentimiento.
     *
     * <p>No es lo mismo que la pregunta de arriba y por eso van separadas. Un
     * atleta de 15 anos pudo entrar en el club con 12: su consentimiento de
     * entonces lo dio su tutor y era valido, y sigue siendolo. Comprobarlo con
     * la edad de hoy daria por invalido lo que no lo es —y al reves, daria por
     * bueno el consentimiento que un nino de 13 firmo solo.
     */
    public static boolean wasUnderConsentAgeAtDecision(Consent consent) {
        return isUnderConsentAgeOn(consent.getAthlete().getBirthDate(), consent.getDecisionDate());
    }

    public static boolean isUnderConsentAgeOn(LocalDate birthDate, LocalDate date) {
        return Period.between(birthDate, date).getYears() < CONSENT_AGE;
    }
}
