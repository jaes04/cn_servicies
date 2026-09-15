package es.jaes.cn_servicies.medical_certificate;

import es.jaes.cn_servicies.athlete.Athlete;
import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.club.ClubService;
import es.jaes.cn_servicies.season.Season;
import es.jaes.cn_servicies.season.SeasonService;
import es.jaes.cn_servicies.tenant.TenantContext;
import es.jaes.cn_servicies.user.User;
import es.jaes.cn_servicies.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class MedicalCertificateService {

    private final MedicalCertificateRepository certificateRepository;
    private final ClubService clubService;
    private final SeasonService seasonService;
    private final UserService userService;

    /**
     * Registra un certificado para una temporada. Recibe el atleta ya resuelto y
     * no su id, por lo mismo que {@code ConsentService.record}: buscarlo aqui
     * crearia una dependencia hacia {@code AthleteService} que en algun momento
     * vuelve.
     *
     * <p><b>Un certificado vale una temporada</b> (bloque 3b): es lo que pide el
     * club, asi que su caducidad es el ultimo dia de la temporada y no una fecha
     * que se teclea.
     *
     * @param validator quien del club comprueba el papel. Hoy siempre es quien
     *                  hace la peticion, porque registrar <em>es</em> validar:
     *                  no hay flujo en que el certificado entre por otra via.
     */
    public MedicalCertificate register(Athlete athlete, MedicalCertificateRequest request,
                                       String validator) {
        // Por el servicio: una temporada de otro club sale como no encontrada.
        Season season = seasonService.findOrThrow(request.getSeasonId());

        if (request.getIssuedOn().isAfter(season.getEndDate())) {
            throw new IllegalArgumentException(
                    "La fecha de emisión es posterior al final de la temporada");
        }

        Club club = clubService.getById(TenantContext.require());
        User user = userService.findEntityByUsername(validator);

        MedicalCertificate certificate = new MedicalCertificate();
        certificate.setClub(club);
        certificate.setAthlete(athlete);
        certificate.setSeason(season);
        certificate.setIssuedOn(request.getIssuedOn());
        certificate.setExpiresOn(season.getEndDate());
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
     * Si el atleta esta cubierto hoy. Es lo que hace falta antes de que entre al
     * agua, y no requiere saber nada mas.
     *
     * <p><b>Manda el certificado de la temporada activa</b>, nunca el ultimo
     * registrado: anotar en agosto el del curso que viene no puede cubrir este.
     * <ul>
     *   <li>{@code VALID} o {@code EXPIRING_SOON}: tiene el de la temporada activa,
     *       y el aviso salta cuando la temporada acaba en 30 dias o menos.</li>
     *   <li>{@code EXPIRED}: el ultimo que trajo es de otra temporada. Hay que
     *       pedirle que renueve.</li>
     *   <li>{@code MISSING}: nunca ha traido ninguno. Que no conste es una
     *       respuesta, no un error, y la mas frecuente al empezar el curso.</li>
     * </ul>
     *
     * <p>Sin temporada activa no hay "la de este curso" contra la que medir, y
     * cuenta el que mas lejos llega.
     */
    @Transactional(readOnly = true)
    public MedicalCertificateStatus statusForAthlete(UUID athleteId) {
        List<MedicalCertificate> certificados =
                certificateRepository.findByAthleteIdOrderByExpiresOnDesc(athleteId);
        if (certificados.isEmpty()) {
            return MedicalCertificateStatus.MISSING;
        }

        LocalDate hoy = LocalDate.now();
        Optional<Season> activa = seasonService.findActiveSeason();
        if (activa.isEmpty()) {
            return certificados.get(0).statusOn(hoy);
        }

        UUID temporadaActiva = activa.get().getId();
        return certificados.stream()
                .filter(certificado -> certificado.getSeason() != null
                        && certificado.getSeason().getId().equals(temporadaActiva))
                .findFirst()
                .map(certificado -> certificado.statusOn(hoy))
                .orElse(MedicalCertificateStatus.EXPIRED);
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
     * <p>Con el certificado por temporada, la caducidad es el final de la
     * temporada, asi que esta lista se llena de golpe en las ultimas semanas del
     * curso. <b>Se mantiene hasta el bloque 3c</b>, donde la sustituye el informe de
     * documentacion pendiente, para que el frontend no se quede sin aviso entre
     * medias.
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
        if (certificate.getSeason() != null) {
            response.setSeasonId(certificate.getSeason().getId());
            response.setSeasonName(certificate.getSeason().getName());
        }
        response.setIssuedOn(certificate.getIssuedOn());
        response.setExpiresOn(certificate.lastValidDay());
        response.setStatus(certificate.statusOn(LocalDate.now()));
        response.setValidatedBy(certificate.getValidatedBy().getUsername());
        response.setValidatedAt(certificate.getValidatedAt());
        return response;
    }
}
