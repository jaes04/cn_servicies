package es.jaes.cn_servicies.medical_certificate;

import es.jaes.cn_servicies.athlete.Athlete;
import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.club.ClubService;
import es.jaes.cn_servicies.tenant.TenantContext;
import es.jaes.cn_servicies.user.User;
import es.jaes.cn_servicies.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class MedicalCertificateService {

    private final MedicalCertificateRepository certificateRepository;
    private final ClubService clubService;
    private final UserService userService;

    /**
     * Registra un certificado. Recibe el atleta ya resuelto y no su id, por lo
     * mismo que {@code ConsentService.record}: buscarlo aqui crearia una
     * dependencia hacia {@code AthleteService} que en algun momento vuelve.
     *
     * @param validator quien del club comprueba el papel. Hoy siempre es quien
     *                  hace la peticion, porque registrar <em>es</em> validar:
     *                  no hay flujo en que el certificado entre por otra via.
     */
    public MedicalCertificate register(Athlete athlete, MedicalCertificateRequest request,
                                       String validator) {
        if (!request.getExpiresOn().isAfter(request.getIssuedOn())) {
            throw new IllegalArgumentException(
                    "La fecha de caducidad tiene que ser posterior a la de emisión");
        }

        Club club = clubService.getById(TenantContext.require());
        User user = userService.findEntityByUsername(validator);

        MedicalCertificate certificate = new MedicalCertificate();
        certificate.setClub(club);
        certificate.setAthlete(athlete);
        certificate.setIssuedOn(request.getIssuedOn());
        certificate.setExpiresOn(request.getExpiresOn());
        certificate.setValidatedBy(user);
        certificate.setValidatedAt(LocalDateTime.now());

        return certificateRepository.save(certificate);
    }

    @Transactional(readOnly = true)
    public List<MedicalCertificateResponse> historyForAthlete(UUID athleteId) {
        return certificateRepository.findByAthleteIdOrderByExpiresOnDesc(athleteId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Si el atleta esta cubierto hoy, y hasta cuando. Es lo que hace falta antes
     * de que entre al agua, y no requiere saber nada mas.
     *
     * <p>Sin certificado devuelve {@code MISSING}, no un error: que no conste es
     * una respuesta, y ademas la mas frecuente al principio de temporada.
     */
    @Transactional(readOnly = true)
    public MedicalCertificateStatus statusForAthlete(UUID athleteId) {
        return certificateRepository.findFirstByAthleteIdOrderByExpiresOnDesc(athleteId)
                .map(certificate -> certificate.statusOn(LocalDate.now()))
                .orElse(MedicalCertificateStatus.MISSING);
    }

    /** Cubierto hoy: vale tanto vigente como a punto de caducar, que todavia cubre. */
    @Transactional(readOnly = true)
    public boolean hasValidCertificate(UUID athleteId) {
        MedicalCertificateStatus status = statusForAthlete(athleteId);
        return status == MedicalCertificateStatus.VALID
                || status == MedicalCertificateStatus.EXPIRING_SOON;
    }

    /**
     * Los que caducan de aqui a {@code days} dias, el mas urgente primero.
     *
     * <p>Es la mitad del "aviso automatico al tutor 30 dias antes" que pide el
     * roadmap: la lista existe y el club la ve. Enviar el correo necesita
     * infraestructura que el proyecto todavia no tiene, y es un bloque aparte.
     */
    @Transactional(readOnly = true)
    public List<MedicalCertificateResponse> expiringWithin(int days) {
        LocalDate hoy = LocalDate.now();
        return certificateRepository
                .findByExpiresOnBetweenOrderByExpiresOnAsc(hoy, hoy.plusDays(days)).stream()
                .map(this::toResponse)
                .toList();
    }

    public MedicalCertificateResponse toResponse(MedicalCertificate certificate) {
        MedicalCertificateResponse response = new MedicalCertificateResponse();
        response.setId(certificate.getId());
        response.setAthleteId(certificate.getAthlete().getId());
        response.setIssuedOn(certificate.getIssuedOn());
        response.setExpiresOn(certificate.getExpiresOn());
        response.setStatus(certificate.statusOn(LocalDate.now()));
        response.setValidatedBy(certificate.getValidatedBy().getUsername());
        response.setValidatedAt(certificate.getValidatedAt());
        return response;
    }
}
