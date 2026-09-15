package es.jaes.cn_servicies.training_group;

import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.club.ClubService;
import es.jaes.cn_servicies.season.Season;
import es.jaes.cn_servicies.season.SeasonService;
import es.jaes.cn_servicies.tenant.TenantContext;
import es.jaes.cn_servicies.user.Role;
import es.jaes.cn_servicies.user.RoleName;
import es.jaes.cn_servicies.user.User;
import es.jaes.cn_servicies.user.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TrainingGroupService {

    private final TrainingGroupRepository groupRepository;
    private final SeasonService seasonService;
    private final UserService userService;
    private final ClubService clubService;
    private final AthleteGroupService membershipService;

    public TrainingGroupResponse create(TrainingGroupRequest request) {
        Club club = clubService.getById(TenantContext.require());
        // Por el servicio y no por el repositorio: si la temporada es de otro
        // club, aqui sale "no encontrada" y no se llega a crear nada.
        Season season = seasonService.findOrThrow(request.getSeasonId());

        if (groupRepository.existsBySeasonIdAndName(season.getId(), request.getName())) {
            throw new IllegalArgumentException(
                    "Ya existe un grupo con ese nombre en esa temporada");
        }

        TrainingGroup group = new TrainingGroup();
        group.setClub(club);
        group.setSeason(season);
        aplicar(group, request);
        return toResponse(groupRepository.save(group));
    }

    public TrainingGroupResponse update(UUID id, TrainingGroupRequest request) {
        TrainingGroup group = findOrThrow(id);
        Season season = seasonService.findOrThrow(request.getSeasonId());

        if (groupRepository.existsBySeasonIdAndNameAndIdNot(
                season.getId(), request.getName(), group.getId())) {
            throw new IllegalArgumentException(
                    "Ya existe un grupo con ese nombre en esa temporada");
        }

        group.setSeason(season);
        aplicar(group, request);
        return toResponse(groupRepository.save(group));
    }

    /**
     * Copia los grupos de una temporada a otra: nombre, categoria, nivel,
     * plazas, entrenador principal y ayudantes.
     *
     * <p><b>No copia la composicion</b>, y no es un olvido: que el equipo cambie
     * cada año es justamente el motivo por el que el grupo cuelga de la
     * temporada. Lo que se ahorra es volver a teclear catorce grupos, no decidir
     * quien va en cada uno.
     *
     * <p>Se niega si la temporada destino ya tiene alguno. Es la proteccion
     * contra el doble clic, que aqui no deja un duplicado sino veintiocho
     * grupos donde deberia haber catorce.
     */
    public List<TrainingGroupResponse> duplicate(DuplicateGroupsRequest request) {
        if (request.getFromSeasonId().equals(request.getToSeasonId())) {
            throw new IllegalArgumentException("El origen y el destino son la misma temporada");
        }

        Season origen = seasonService.findOrThrow(request.getFromSeasonId());
        Season destino = seasonService.findOrThrow(request.getToSeasonId());

        if (groupRepository.existsBySeasonId(destino.getId())) {
            throw new IllegalArgumentException(
                    "La temporada de destino ya tiene grupos: duplicar los añadiría a los que hay");
        }

        List<TrainingGroup> copias = groupRepository
                .findBySeasonIdOrderByNameAsc(origen.getId()).stream()
                .map(original -> {
                    TrainingGroup copia = new TrainingGroup();
                    copia.setClub(original.getClub());
                    copia.setSeason(destino);
                    copia.setName(original.getName());
                    copia.setCategory(original.getCategory());
                    copia.setLevel(original.getLevel());
                    copia.setMaxSlots(original.getMaxSlots());
                    copia.setCoach(original.getCoach());
                    copia.setAssistantCoaches(new HashSet<>(original.getAssistantCoaches()));
                    return copia;
                })
                .toList();

        if (copias.isEmpty()) {
            throw new IllegalArgumentException("La temporada de origen no tiene grupos que copiar");
        }

        return groupRepository.saveAll(copias).stream().map(this::toResponse).toList();
    }

    /** Borrado logico: cuando existan pertenencias, borrar de verdad se llevaria el historico. */
    public void softDelete(UUID id) {
        TrainingGroup group = findOrThrow(id);
        group.setDeletedAt(LocalDateTime.now());
        groupRepository.save(group);
    }

    @Transactional(readOnly = true)
    public List<TrainingGroupResponse> findAll(UUID seasonId) {
        return findAll(seasonId, null);
    }

    /**
     * Grupos del club, o de una temporada, acotados a {@code onlyIds}.
     *
     * @param onlyIds los grupos que puede ver quien pide; {@code null} no acota.
     *                Es lo que usa el listado de un entrenador (tarea S.3.3.b),
     *                que solo ve los suyos.
     */
    @Transactional(readOnly = true)
    public List<TrainingGroupResponse> findAll(UUID seasonId, Collection<UUID> onlyIds) {
        List<TrainingGroup> grupos = (seasonId == null
                ? groupRepository.findAllByOrderByNameAsc()
                : groupRepository.findBySeasonIdOrderByNameAsc(seasonId)).stream()
                .filter(grupo -> onlyIds == null || onlyIds.contains(grupo.getId()))
                .toList();

        // Los conteos en una sola consulta: catorce grupos no pueden costar
        // catorce viajes a la base para dibujar una tabla.
        Map<UUID, Long> conteos = membershipService.openCountByGroup(
                grupos.stream().map(TrainingGroup::getId).toList());

        return grupos.stream()
                .map(grupo -> toResponse(grupo, conteos.getOrDefault(grupo.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public TrainingGroupResponse findById(UUID id) {
        TrainingGroup grupo = findOrThrow(id);
        return toResponse(grupo, membershipService.currentMemberCount(grupo.getId()));
    }

    public TrainingGroup findOrThrow(UUID id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Grupo no encontrado"));
    }

    /** Los grupos que lleva ese usuario, como principal o como ayudante, en cualquier temporada. */
    @Transactional(readOnly = true)
    public Set<UUID> coachedGroupIds(UUID userId) {
        return new HashSet<>(groupRepository.findIdsCoachedBy(userId));
    }

    private void aplicar(TrainingGroup group, TrainingGroupRequest request) {
        group.setName(request.getName());
        group.setCategory(request.getCategory());
        group.setLevel(request.getLevel());
        group.setMaxSlots(request.getMaxSlots());
        group.setCoach(request.getCoachId() == null ? null : resolverEntrenador(request.getCoachId()));
        group.setAssistantCoaches(resolverAyudantes(request));
    }

    private Set<User> resolverAyudantes(TrainingGroupRequest request) {
        Set<UUID> ids = request.getAssistantCoachIds();
        if (ids == null || ids.isEmpty()) {
            return new HashSet<>();
        }
        // Se rechaza en vez de quitarlo sin decir nada: si llega asi es que la
        // interfaz permite un estado que no deberia, y conviene que se note.
        if (request.getCoachId() != null && ids.contains(request.getCoachId())) {
            throw new IllegalArgumentException(
                    "El entrenador principal no puede figurar también como ayudante");
        }
        return ids.stream().map(this::resolverEntrenador).collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * El usuario, siempre que pueda entrenar.
     *
     * <p>Hasta la tarea S.3.3.b esto no se comprobaba, a proposito: estar en un
     * grupo no daba ningun permiso, asi que una asignacion equivocada era un
     * error de datos. <b>Ahora da acceso al grupo y a sus menores</b>, y asignar a
     * un socio pasaria de error de datos a agujero — aunque hoy las reglas de
     * {@code SecurityConfig} por rol lo taparian, no puede depender de eso.
     *
     * <p>Por servicio y no por repositorio: un usuario de otro club sale como no
     * encontrado.
     */
    private User resolverEntrenador(UUID userId) {
        User user = userService.findEntityById(userId);
        boolean puedeEntrenar = user.getRoles().stream()
                .map(Role::getName)
                .anyMatch(rol -> rol == RoleName.ROLE_TECHNICAL_STAFF || rol == RoleName.ROLE_ADMIN);
        if (!puedeEntrenar) {
            throw new IllegalArgumentException(
                    "Solo se puede asignar como entrenador a personal técnico del club");
        }
        return user;
    }

    private TrainingGroupResponse toResponse(TrainingGroup group) {
        return toResponse(group, membershipService.currentMemberCount(group.getId()));
    }

    private TrainingGroupResponse toResponse(TrainingGroup group, long memberCount) {
        TrainingGroupResponse response = new TrainingGroupResponse();
        response.setId(group.getId());
        response.setSeasonId(group.getSeason().getId());
        response.setSeasonName(group.getSeason().getName());
        response.setName(group.getName());
        response.setCategory(group.getCategory());
        response.setLevel(group.getLevel());
        response.setMaxSlots(group.getMaxSlots());
        response.setMemberCount(memberCount);
        if (group.getCoach() != null) {
            response.setCoachId(group.getCoach().getId());
            response.setCoachUsername(group.getCoach().getUsername());
        }
        response.setAssistantCoaches(group.getAssistantCoaches().stream()
                .sorted(Comparator.comparing(User::getUsername))
                .map(user -> new GroupCoachResponse(user.getId(), user.getUsername()))
                .toList());
        return response;
    }
}
