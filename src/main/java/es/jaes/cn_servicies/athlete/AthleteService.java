package es.jaes.cn_servicies.athlete;

import es.jaes.cn_servicies.athlete_link.UserAthleteRepository;
import es.jaes.cn_servicies.athlete_link.UserAthleteType;
import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.club.ClubService;
import es.jaes.cn_servicies.guardian.AthleteGuardianRequest;
import es.jaes.cn_servicies.guardian.ConsentRequest;
import es.jaes.cn_servicies.guardian.ConsentService;
import es.jaes.cn_servicies.guardian.ConsentType;
import es.jaes.cn_servicies.guardian.Guardian;
import es.jaes.cn_servicies.guardian.GuardianService;
import es.jaes.cn_servicies.tenant.TenantContext;
import es.jaes.cn_servicies.user.User;
import es.jaes.cn_servicies.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AthleteService {

    private final AthleteRepository athleteRepository;
    private final GenderRepository genderRepository;
    private final UserAthleteRepository userAthleteRepository;
    private final UserRepository userRepository;
    private final ClubService clubService;
    private final GuardianService guardianService;
    private final ConsentService consentService;

    public AthleteResponse create(AthleteRequest request) {
        // Del contexto, no del club por defecto: los endpoints de atletas van
        // siempre autenticados, asi que la peticion trae club. Si no lo trae,
        // require() revienta en vez de inventarse uno.
        Club club = clubService.getById(TenantContext.require());

        String documento = normalizarDocumento(request.getDni());
        comprobarQueNoEstaRepetido(club, documento, request, null);

        Athlete athlete = new Athlete();
        athlete.setClub(club);
        athlete.setFirstName(request.getFirstName());
        athlete.setLastName(request.getLastName());
        athlete.setBirthDate(request.getBirthDate());
        athlete.setDni(documento);
        athlete.setGender(resolveGender(request.getGender()));
        Athlete saved = athleteRepository.save(athlete);

        registerGuardianConsent(saved, request.getGuardianConsent());

        return toResponse(saved);
    }

    /**
     * Tutor y consentimientos del alta, y la regla que impide dar de alta a un
     * menor de 14 sin ellos.
     *
     * <p>Va dentro de la misma transaccion que el alta a proposito: si algo de
     * esto falla, el atleta tampoco queda creado. Una ficha de menor sin
     * consentimiento seria exactamente lo que la regla existe para evitar, y
     * dejarla escrita "porque el primer save ya habia pasado" es peor que no
     * tener regla.
     */
    private void registerGuardianConsent(Athlete athlete, AthleteGuardianRequest request) {
        if (request == null) {
            if (ConsentService.requiresGuardianConsent(athlete)) {
                throw new IllegalArgumentException(
                        "Un atleta menor de 14 años no puede darse de alta sin el"
                                + " consentimiento de su tutor");
            }
            return;
        }

        // El de imagen puede ser que no; este no. Sin base legal para tratar sus
        // datos no hay ficha que valga, tenga la edad que tenga.
        if (!Boolean.TRUE.equals(request.getDataProcessing())) {
            throw new IllegalArgumentException(
                    "El consentimiento para el tratamiento de datos es obligatorio");
        }

        Guardian guardian = guardianService.resolveOrCreate(request.getGuardian());
        // Antes de los consentimientos: registrarlos comprueba que el vinculo
        // existe, que es lo que impide que un tutor consienta por un menor ajeno.
        guardianService.link(athlete, guardian, request.getRelationship());

        record(athlete, guardian, request, ConsentType.DATA_PROCESSING, true);
        record(athlete, guardian, request, ConsentType.IMAGE, request.getImage());
    }

    private void record(Athlete athlete, Guardian guardian, AthleteGuardianRequest source,
                        ConsentType type, Boolean granted) {
        ConsentRequest consent = new ConsentRequest();
        consent.setGuardianId(guardian.getId());
        consent.setType(type);
        consent.setGranted(Boolean.TRUE.equals(granted));
        consent.setDecisionDate(source.getDecisionDate());
        consent.setEvidenceType(source.getEvidenceType());
        consent.setEvidenceRef(source.getEvidenceRef());

        // Sin IP: el alta la hace el club desde su panel, asi que la IP seria la
        // de quien teclea, no la de quien consiente. No prueba nada.
        consentService.record(athlete, consent, null);
    }

    @Transactional(readOnly = true)
    public AthleteResponse findById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<AthleteResponse> findAll(String q, Gender gender, Pageable pageable) {
        return findAll(q, gender, pageable, null);
    }

    /**
     * El listado, acotado a {@code onlyIds}.
     *
     * @param onlyIds los atletas que puede ver quien pide; {@code null} no acota,
     *                y una coleccion vacia no devuelve nada. Es lo que usa el
     *                listado de un entrenador (tarea S.3.3.b).
     */
    @Transactional(readOnly = true)
    public Page<AthleteResponse> findAll(String q, Gender gender, Pageable pageable,
                                         java.util.Collection<UUID> onlyIds) {
        if (onlyIds != null && onlyIds.isEmpty()) {
            return Page.empty(pageable);
        }
        Specification<Athlete> spec = Specification.where(null);
        if (onlyIds != null) spec = spec.and(AthleteSpecification.idIn(onlyIds));
        if (q != null && !q.isBlank()) spec = spec.and(AthleteSpecification.nameOrDniContains(q));
        if (gender != null) spec = spec.and(AthleteSpecification.hasGender(gender));
        return athleteRepository.findAll(spec, pageable).map(this::toResponse);
    }

    public AthleteResponse update(UUID id, AthleteRequest request) {
        Athlete athlete = findOrThrow(id);

        // Se rechaza en vez de ignorarse: aceptar el bloque y no hacer nada con
        // el dejaria creer que el consentimiento quedo registrado.
        if (request.getGuardianConsent() != null) {
            throw new IllegalArgumentException(
                    "El tutor y sus consentimientos no se modifican desde la ficha del atleta");
        }

        // Acotado al club del propio atleta, no al club por defecto: es el suyo
        // el que no puede tener dos fichas de la misma persona.
        String documento = normalizarDocumento(request.getDni());
        comprobarQueNoEstaRepetido(athlete.getClub(), documento, request, athlete);

        athlete.setFirstName(request.getFirstName());
        athlete.setLastName(request.getLastName());
        athlete.setBirthDate(request.getBirthDate());
        athlete.setDni(documento);
        athlete.setGender(resolveGender(request.getGender()));
        return toResponse(athleteRepository.save(athlete));
    }

    /**
     * Que la ficha no sea la de alguien que ya esta en el club.
     *
     * <p><b>Con documento</b>, manda el documento: el mismo en dos fichas es un
     * error, igual que antes del bloque 3b.
     *
     * <p><b>Sin documento</b> no hay nada exacto con que comparar, y el indice unico
     * no ayuda: deja convivir a todas las fichas sin documento. Lo que se compara
     * entonces es nombre, apellidos y fecha de nacimiento, <b>contra cualquier
     * ficha del club, tenga documento o no</b>. Sin esto, un doble clic en el
     * formulario deja dos fichas del mismo niño, con su asistencia repartida entre
     * las dos. Los gemelos no chocan: tienen nombres distintos.
     *
     * @param actual la ficha que se edita, para no contarla contra si misma;
     *               {@code null} en el alta
     */
    private void comprobarQueNoEstaRepetido(Club club, String documento,
                                            AthleteRequest request, Athlete actual) {
        if (documento != null) {
            boolean cambia = actual == null || !documento.equals(actual.getDni());
            if (cambia && athleteRepository.existsByClubAndDni(club, documento)) {
                throw new IllegalArgumentException("Ya existe un atleta con ese DNI");
            }
            return;
        }

        boolean repetido = actual == null
                ? athleteRepository.existsByClubAndFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDate(
                        club, request.getFirstName(), request.getLastName(), request.getBirthDate())
                : athleteRepository.existsByClubAndFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndIdNot(
                        club, request.getFirstName(), request.getLastName(), request.getBirthDate(),
                        actual.getId());
        if (repetido) {
            throw new IllegalArgumentException(
                    "Ya existe un atleta con el mismo nombre, apellidos y fecha de nacimiento."
                            + " Si es otra persona, indica su documento de identidad");
        }
    }

    /**
     * Sin espacios y en mayusculas, y {@code null} si viene vacio. Sin esto,
     * {@code x1234567l} y {@code X1234567L} serian dos documentos distintos, y dos
     * fichas con {@code ""} chocarian en el indice unico, que si considera iguales
     * dos cadenas vacias aunque no dos nulos.
     */
    static String normalizarDocumento(String documento) {
        if (documento == null || documento.isBlank()) {
            return null;
        }
        return documento.trim().toUpperCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public List<AthleteResponse> findTuteesByUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        return userAthleteRepository.findByUserIdAndType(user.getId(), UserAthleteType.TUTOR).stream()
                .map(link -> toResponse(link.getAthlete()))
                .toList();
    }

    public void softDelete(UUID id) {
        Athlete athlete = findOrThrow(id);
        athlete.setDeletedAt(LocalDateTime.now());
        athleteRepository.save(athlete);
    }

    public Athlete findOrThrow(UUID id) {
        return athleteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Atleta no encontrado"));
    }

    private GenderEntity resolveGender(Gender gender) {
        return genderRepository.findByName(gender)
                .orElseThrow(() -> new IllegalArgumentException("Género no encontrado: " + gender));
    }

    private AthleteResponse toResponse(Athlete athlete) {
        AthleteResponse response = new AthleteResponse();
        response.setId(athlete.getId());
        response.setFirstName(athlete.getFirstName());
        response.setLastName(athlete.getLastName());
        response.setBirthDate(athlete.getBirthDate());
        response.setDni(athlete.getDni());
        response.setGender(athlete.getGender().getName());
        response.setCreatedAt(athlete.getCreatedAt());
        return response;
    }
}
