package es.jaes.cn_servicies.guardian;

import es.jaes.cn_servicies.athlete.Athlete;
import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.club.ClubService;
import es.jaes.cn_servicies.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class GuardianService {

    private final GuardianRepository guardianRepository;
    private final AthleteGuardianRepository athleteGuardianRepository;
    private final ClubService clubService;

    /**
     * Devuelve la ficha del tutor con ese DNI en el club, y la crea si no
     * existe. Lo primero es lo habitual en cuanto hay hermanos: el segundo hijo
     * no da de alta a un padre nuevo.
     *
     * <p><b>No actualiza los datos de contacto de una ficha existente.</b> Si el
     * telefono que llega difiere del que hay guardado, no se sabe cual de los
     * dos esta al dia, y machacarlo en silencio desde el alta de otro atleta es
     * la peor de las dos opciones. Se modifica desde el tutor, no de rebote.
     */
    public Guardian resolveOrCreate(GuardianRequest request) {
        Club club = clubService.getById(TenantContext.require());

        // Sin espacios y en mayusculas, igual que el documento del atleta: si no,
        // el segundo hijo tecleado con el NIE en minusculas crearia otro tutor.
        String documento = request.getDni() == null
                ? null
                : request.getDni().trim().toUpperCase(java.util.Locale.ROOT);

        return guardianRepository.findByClubAndDni(club, documento)
                .orElseGet(() -> {
                    Guardian guardian = new Guardian();
                    guardian.setClub(club);
                    guardian.setFirstName(request.getFirstName());
                    guardian.setLastName(request.getLastName());
                    guardian.setDni(documento);
                    guardian.setEmail(request.getEmail());
                    guardian.setPhone(request.getPhone());
                    return guardianRepository.save(guardian);
                });
    }

    /** Idempotente: repetir el vinculo no lo duplica ni revienta contra el indice unico. */
    public AthleteGuardian link(Athlete athlete, Guardian guardian, GuardianRelationship relationship) {
        return athleteGuardianRepository
                .findByAthleteId(athlete.getId()).stream()
                .filter(link -> link.getGuardian().getId().equals(guardian.getId()))
                .findFirst()
                .orElseGet(() -> {
                    AthleteGuardian link = new AthleteGuardian();
                    link.setAthlete(athlete);
                    link.setGuardian(guardian);
                    link.setRelationship(relationship);
                    return athleteGuardianRepository.save(link);
                });
    }

    @Transactional(readOnly = true)
    public List<AthleteGuardian> findByAthlete(UUID athleteId) {
        return athleteGuardianRepository.findByAthleteId(athleteId);
    }

    /**
     * El listado de tutores del club, acotado a {@code onlyAthleteIds}.
     *
     * @param onlyAthleteIds los atletas que puede ver quien pide; {@code null} no
     *                       acota, y una coleccion vacia no devuelve nada. Acota
     *                       dos veces: que tutores salen, y que atletas aparecen
     *                       en cada uno. Sin lo segundo, el entrenador veria el
     *                       nombre del hermano que nada en otro grupo.
     */
    @Transactional(readOnly = true)
    public Page<GuardianResponse> findAll(String q, Pageable pageable, Collection<UUID> onlyAthleteIds) {
        if (onlyAthleteIds != null && onlyAthleteIds.isEmpty()) {
            return Page.empty(pageable);
        }
        Specification<Guardian> spec = Specification.where(null);
        if (onlyAthleteIds != null) spec = spec.and(GuardianSpecification.linkedToAnyAthlete(onlyAthleteIds));
        if (q != null && !q.isBlank()) spec = spec.and(GuardianSpecification.nameDniOrEmailContains(q));

        Page<Guardian> page = guardianRepository.findAll(spec, pageable);
        if (page.isEmpty()) {
            return page.map(g -> toResponse(g, List.of()));
        }

        // Una consulta para los vinculos de toda la pagina, no una por tutor.
        Map<UUID, List<AthleteGuardian>> linksByGuardian = athleteGuardianRepository
                .findActiveByGuardianIdIn(page.map(Guardian::getId).getContent()).stream()
                .filter(link -> onlyAthleteIds == null || onlyAthleteIds.contains(link.getAthlete().getId()))
                .collect(Collectors.groupingBy(link -> link.getGuardian().getId()));

        return page.map(g -> toResponse(g, linksByGuardian.getOrDefault(g.getId(), List.of())));
    }

    /**
     * Da de alta un tutor para un atleta que ya existe —el segundo progenitor, o
     * el de un atleta que se dio de alta sin tutor— y los vincula.
     *
     * <p>Mismo criterio que el alta del atleta: si ya hay un tutor con ese
     * documento en el club se reutiliza, sin tocar sus datos de contacto; eso se
     * corrige con {@link #update}. Si ya estaba vinculado a este atleta, no se
     * duplica y se conserva el parentesco que tenia.
     *
     * <p><b>No registra consentimientos.</b> Van por {@code /api/consents}, que
     * exige justamente que este vinculo exista.
     *
     * @param onlyAthleteIds acota los atletas de la respuesta, como en {@link #findAll}
     */
    public GuardianResponse addToAthlete(Athlete athlete, GuardianLinkRequest request,
                                         Collection<UUID> onlyAthleteIds) {
        Guardian guardian = resolveOrCreate(request.getGuardian());
        link(athlete, guardian, request.getRelationship());
        return toScopedResponse(guardian, onlyAthleteIds);
    }

    /**
     * Corrige el nombre y el contacto de un tutor. El documento no se cambia: es
     * la clave por la que se reconoce a la persona, y lo que firmo con el tiene
     * que seguir apuntando a quien lo firmo.
     */
    public GuardianResponse update(UUID id, GuardianUpdateRequest request, Collection<UUID> onlyAthleteIds) {
        Guardian guardian = findOrThrow(id);
        guardian.setFirstName(request.getFirstName());
        guardian.setLastName(request.getLastName());
        guardian.setEmail(request.getEmail());
        guardian.setPhone(request.getPhone());
        return toScopedResponse(guardianRepository.save(guardian), onlyAthleteIds);
    }

    /**
     * Carga por id, que pasa por RLS y por el borrado logico: un tutor de otro
     * club o borrado da 404 igual que uno que no existe.
     */
    @Transactional(readOnly = true)
    public Guardian findOrThrow(UUID id) {
        return guardianRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tutor no encontrado"));
    }

    /** Los atletas vinculados a ese tutor. Es lo que usa {@code AccessGuard} para decidir si un entrenador llega a el. */
    @Transactional(readOnly = true)
    public Set<UUID> athleteIdsOf(UUID guardianId) {
        return athleteGuardianRepository.findActiveByGuardianIdIn(List.of(guardianId)).stream()
                .map(link -> link.getAthlete().getId())
                .collect(Collectors.toSet());
    }

    private GuardianResponse toScopedResponse(Guardian guardian, Collection<UUID> onlyAthleteIds) {
        List<AthleteGuardian> links = athleteGuardianRepository
                .findActiveByGuardianIdIn(List.of(guardian.getId())).stream()
                .filter(link -> onlyAthleteIds == null || onlyAthleteIds.contains(link.getAthlete().getId()))
                .toList();
        return toResponse(guardian, links);
    }

    private GuardianResponse toResponse(Guardian guardian, List<AthleteGuardian> links) {
        List<GuardianResponse.LinkedAthlete> athletes = links.stream()
                .map(link -> new GuardianResponse.LinkedAthlete(
                        link.getAthlete().getId(),
                        link.getAthlete().getFirstName() + " " + link.getAthlete().getLastName(),
                        link.getRelationship()))
                .toList();
        return new GuardianResponse(
                guardian.getId(),
                guardian.getFirstName(),
                guardian.getLastName(),
                guardian.getDni(),
                guardian.getEmail(),
                guardian.getPhone(),
                guardian.getUser() != null,
                athletes,
                guardian.getCreatedAt());
    }
}
