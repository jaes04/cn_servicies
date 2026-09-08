package es.jaes.cn_servicies.training_group;

import es.jaes.cn_servicies.athlete.Athlete;
import es.jaes.cn_servicies.athlete.AthleteService;
import es.jaes.cn_servicies.season.Season;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AthleteGroupService {

    private final AthleteGroupRepository membershipRepository;
    private final TrainingGroupRepository groupRepository;
    private final AthleteService athleteService;

    /**
     * Da de alta a un atleta en un grupo.
     *
     * <p><b>Varios grupos a la vez estan permitidos</b> —natacion y preparacion
     * fisica es el caso corriente—, asi que dar de alta en uno no cierra las
     * pertenencias de los demas. Lo unico prohibido es estar dos veces abierto
     * en el <b>mismo</b> grupo.
     *
     * <p>Para mover a un atleta de grupo, {@code replacesGroupId} cierra la
     * pertenencia de ese otro grupo en la misma transaccion. Es explicito a
     * proposito: "cerrar la anterior" no puede deducirse cuando un atleta
     * legitimamente pertenece a dos sitios, y adivinarlo daria de baja al
     * nadador de preparacion fisica cada vez que cambia de grupo de natacion.
     */
    public AthleteGroup assign(UUID athleteId, UUID groupId, LocalDate joinedOn,
                               UUID replacesGroupId) {
        Athlete athlete = athleteService.findOrThrow(athleteId);
        TrainingGroup group = grupoOException(groupId);

        Season season = group.getSeason();
        if (!season.covers(joinedOn)) {
            throw new IllegalArgumentException(
                    "La fecha de alta cae fuera de la temporada del grupo");
        }
        if (membershipRepository
                .findByAthleteIdAndTrainingGroupIdAndLeftOnIsNull(athleteId, groupId)
                .isPresent()) {
            throw new IllegalArgumentException("El atleta ya está dado de alta en ese grupo");
        }

        if (replacesGroupId != null) {
            if (replacesGroupId.equals(groupId)) {
                throw new IllegalArgumentException(
                        "El grupo que se deja y el que se ocupa son el mismo");
            }
            leave(athleteId, replacesGroupId, joinedOn, LeaveReason.GROUP_CHANGE);
        }

        AthleteGroup membership = new AthleteGroup();
        membership.setAthlete(athlete);
        membership.setTrainingGroup(group);
        membership.setJoinedOn(joinedOn);
        return membershipRepository.save(membership);
    }

    /**
     * Cierra la pertenencia. <b>Nunca borra la fila</b>: el historico de quien
     * estuvo en el grupo es justamente lo que hay que conservar.
     */
    public AthleteGroup leave(UUID athleteId, UUID groupId, LocalDate leftOn, LeaveReason reason) {
        AthleteGroup membership = membershipRepository
                .findByAthleteIdAndTrainingGroupIdAndLeftOnIsNull(athleteId, groupId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "El atleta no tiene una pertenencia abierta en ese grupo"));

        if (leftOn.isBefore(membership.getJoinedOn())) {
            throw new IllegalArgumentException(
                    "La fecha de baja no puede ser anterior a la de alta");
        }

        membership.setLeftOn(leftOn);
        membership.setLeaveReason(reason);
        return membershipRepository.save(membership);
    }

    /**
     * Miembros del grupo en una fecha dada. Es la consulta que sostiene la
     * asistencia: con ella se sabe a quien habia que pasar lista aquel dia, y no
     * a quien esta hoy en el grupo.
     */
    @Transactional(readOnly = true)
    public List<AthleteGroup> membersOn(UUID groupId, LocalDate date) {
        grupoOException(groupId);
        return membershipRepository.findMembersOn(groupId, date);
    }

    /** Miembros de hoy. Atajo del de arriba, para no repetir {@code LocalDate.now()} por ahi. */
    @Transactional(readOnly = true)
    public List<AthleteGroup> currentMembers(UUID groupId) {
        return membersOn(groupId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public long currentMemberCount(UUID groupId) {
        return membershipRepository.countByTrainingGroupIdAndLeftOnIsNull(groupId);
    }

    /** Historico de un atleta: todos sus grupos, o solo los de una temporada. */
    @Transactional(readOnly = true)
    public List<AthleteGroup> historyOf(UUID athleteId, UUID seasonId) {
        athleteService.findOrThrow(athleteId);
        return seasonId == null
                ? membershipRepository.findByAthleteIdOrderByJoinedOnDesc(athleteId)
                : membershipRepository.findByAthleteAndSeason(athleteId, seasonId);
    }

    @Transactional(readOnly = true)
    public List<AthleteGroup> openMembershipsOf(UUID athleteId) {
        athleteService.findOrThrow(athleteId);
        return membershipRepository.findByAthleteIdAndLeftOnIsNull(athleteId);
    }

    // ----------------------------------------------------------------
    //  Cara a la API
    // ----------------------------------------------------------------

    /**
     * Da de alta a varios atletas de una vez.
     *
     * <p><b>Entera o nada.</b> Va toda en la misma transaccion, asi que si uno
     * de la lista ya estaba en el grupo no entra ninguno. Media asignacion es
     * peor que ninguna: el club se queda sin saber quien entro y quien no, y lo
     * descubre pasando lista.
     */
    public List<AthleteGroupResponse> assignAll(UUID groupId, AssignAthletesRequest request) {
        return request.getAthleteIds().stream()
                .distinct()
                .map(athleteId -> assign(athleteId, groupId,
                        request.getJoinedOn(), request.getReplacesGroupId()))
                .map(this::toResponse)
                .toList();
    }

    public AthleteGroupResponse leaveGroup(UUID groupId, UUID athleteId,
                                           LocalDate leftOn, LeaveReason reason) {
        return toResponse(leave(athleteId, groupId,
                leftOn != null ? leftOn : LocalDate.now(), reason));
    }

    /** Miembros del grupo en una fecha; sin fecha, los de hoy. */
    @Transactional(readOnly = true)
    public List<AthleteGroupResponse> membersOnAsResponse(UUID groupId, LocalDate date) {
        return membersOn(groupId, date != null ? date : LocalDate.now()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AthleteGroupResponse> historyAsResponse(UUID athleteId, UUID seasonId) {
        return historyOf(athleteId, seasonId).stream().map(this::toResponse).toList();
    }

    /**
     * Conteo de miembros abiertos por grupo, en una sola consulta. Lo usa el
     * listado de grupos.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Long> openCountByGroup(Collection<UUID> groupIds) {
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        return membershipRepository.countOpenByGroup(groupIds).stream()
                .collect(Collectors.toMap(fila -> (UUID) fila[0], fila -> (Long) fila[1]));
    }

    private AthleteGroupResponse toResponse(AthleteGroup membership) {
        AthleteGroupResponse response = new AthleteGroupResponse();
        response.setId(membership.getId());
        response.setAthleteId(membership.getAthlete().getId());
        response.setAthleteName(membership.getAthlete().getFirstName()
                + " " + membership.getAthlete().getLastName());
        response.setGroupId(membership.getTrainingGroup().getId());
        response.setGroupName(membership.getTrainingGroup().getName());
        response.setJoinedOn(membership.getJoinedOn());
        response.setLeftOn(membership.getLeftOn());
        response.setLeaveReason(membership.getLeaveReason());
        response.setOpen(membership.isOpen());
        return response;
    }

    /**
     * Por el repositorio del propio modulo, no por el de otro: el grupo vive
     * aqui. Si es de otro club, el filtro y RLS hacen que no aparezca y sale
     * como no encontrado, que es 404 y no 403.
     */
    private TrainingGroup grupoOException(UUID groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Grupo no encontrado"));
    }
}
