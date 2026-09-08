package es.jaes.cn_servicies.training_group;

import es.jaes.cn_servicies.season.Season;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Horarios recurrentes de un grupo (tarea 2.1).
 *
 * <p>Todo entra por el grupo. No hay ningun metodo que acepte un id de horario
 * a secas: {@code group_schedules} no tiene {@code club_id} ni policy de RLS
 * —es tabla hija—, asi que un {@code findById} suyo devolveria el horario de
 * otro club sin rechistar. Lo que tapa eso es cargar primero el grupo, que si
 * esta filtrado, y comprobar que el horario cuelga de el.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GroupScheduleService {

    /** Lunes primero y luego por hora, que es como lo lee una persona. */
    private static final Comparator<GroupSchedule> ORDEN_NATURAL =
            Comparator.comparing(GroupSchedule::getDayOfWeek)
                    .thenComparing(GroupSchedule::getStartTime);

    private final GroupScheduleRepository scheduleRepository;
    private final TrainingGroupRepository groupRepository;

    public GroupScheduleResponse create(UUID groupId, GroupScheduleRequest request) {
        TrainingGroup group = grupoOException(groupId);

        GroupSchedule schedule = new GroupSchedule();
        schedule.setTrainingGroup(group);
        aplicar(schedule, request);
        validar(group, schedule);

        return toResponse(scheduleRepository.save(schedule));
    }

    /**
     * Cambiar un horario ya vigente <b>no puede reescribir lo que ya se
     * entreno</b>. Hoy eso no se nota porque no hay sesiones; en cuanto la 2.2
     * las genere, la regeneracion tiene que tocar solo las futuras. Queda
     * anotado aqui porque es este metodo el que la disparara.
     */
    public GroupScheduleResponse update(UUID groupId, UUID scheduleId,
                                        GroupScheduleRequest request) {
        TrainingGroup group = grupoOException(groupId);
        GroupSchedule schedule = delGrupoOException(groupId, scheduleId);

        aplicar(schedule, request);
        validar(group, schedule);

        return toResponse(scheduleRepository.save(schedule));
    }

    /**
     * Borrado logico. La fila se queda porque en la 2.2 las sesiones colgaran
     * del horario que las genero, y una sesion cuyo horario ha desaparecido no
     * sabe explicar de donde salio.
     *
     * <p>Que hacer con las sesiones futuras ya generadas al borrar un horario es
     * decision de la 2.2, no de aqui.
     */
    public void softDelete(UUID groupId, UUID scheduleId) {
        GroupSchedule schedule = delGrupoOException(groupId, scheduleId);
        schedule.setDeletedAt(LocalDateTime.now());
        scheduleRepository.save(schedule);
    }

    /** Sin fecha, todos los del grupo; con fecha, los que estaban en vigor ese dia. */
    @Transactional(readOnly = true)
    public List<GroupScheduleResponse> findAll(UUID groupId, LocalDate date) {
        grupoOException(groupId);

        List<GroupSchedule> horarios = date == null
                ? scheduleRepository.findByTrainingGroupId(groupId)
                : scheduleRepository.findInForceOn(groupId, date);

        return horarios.stream().sorted(ORDEN_NATURAL).map(this::toResponse).toList();
    }

    /**
     * Los horarios en vigor de un grupo una fecha dada, como entidad.
     *
     * <p>Es la puerta por la que entrara el generador de sesiones de la 2.2:
     * por el servicio y no por el repositorio, que es la regla de la frontera
     * entre modulos.
     */
    @Transactional(readOnly = true)
    public List<GroupSchedule> inForceOn(UUID groupId, LocalDate date) {
        grupoOException(groupId);
        return scheduleRepository.findInForceOn(groupId, date).stream()
                .sorted(ORDEN_NATURAL)
                .toList();
    }

    // ----------------------------------------------------------------
    //  Reglas
    // ----------------------------------------------------------------

    private void validar(TrainingGroup group, GroupSchedule schedule) {
        if (!schedule.getEndTime().isAfter(schedule.getStartTime())) {
            throw new IllegalArgumentException(
                    "La hora de fin tiene que ser posterior a la de inicio");
        }
        if (schedule.getValidUntil() != null
                && schedule.getValidUntil().isBefore(schedule.getValidFrom())) {
            throw new IllegalArgumentException(
                    "El fin de vigencia no puede ser anterior al inicio");
        }

        // La temporada del grupo acota el horario por los dos lados: un horario
        // vigente fuera de ella generaria sesiones de una temporada que no
        // existe. Mismo criterio que el alta de un atleta en un grupo (1.3).
        Season season = group.getSeason();
        if (!season.covers(schedule.getValidFrom())) {
            throw new IllegalArgumentException(
                    "El inicio de vigencia cae fuera de la temporada del grupo");
        }
        if (schedule.getValidUntil() != null && !season.covers(schedule.getValidUntil())) {
            throw new IllegalArgumentException(
                    "El fin de vigencia cae fuera de la temporada del grupo");
        }

        comprobarSolape(group.getId(), schedule);
    }

    /**
     * Dos horarios del mismo grupo no pueden pisarse: mismo dia, franjas que se
     * solapan y vigencias que coinciden significan dos sesiones a la misma hora
     * para los mismos nadadores, y en la 2.2 eso se multiplica por cada semana.
     *
     * <p><b>Solo en el servicio.</b> Llevarlo a la base pediria una restriccion
     * de exclusion con {@code btree_gist}, y una extension que exige
     * superusuario en cada despliegue es desproporcionada para prevenir un error
     * de tecleo. Es la misma decision que se tomo con el solape de temporadas en
     * la 1.1.
     *
     * <p>Entre grupos <b>distintos</b> no se comprueba nada: dos grupos a la
     * misma hora es normal, y sin ubicacion no hay ocupacion de calle que
     * validar.
     */
    private void comprobarSolape(UUID groupId, GroupSchedule candidato) {
        boolean choca = scheduleRepository.findByTrainingGroupId(groupId).stream()
                // En una edicion el propio horario esta en la lista y siempre
                // chocaria consigo mismo.
                .filter(otro -> !otro.getId().equals(candidato.getId()))
                .anyMatch(candidato::conflictsWith);

        if (choca) {
            throw new IllegalArgumentException(
                    "El grupo ya tiene un horario que se solapa con ese dia y hora");
        }
    }

    private void aplicar(GroupSchedule schedule, GroupScheduleRequest request) {
        schedule.setDayOfWeek(request.getDayOfWeek());
        schedule.setStartTime(request.getStartTime());
        schedule.setEndTime(request.getEndTime());
        schedule.setModality(request.getModality());
        schedule.setValidFrom(request.getValidFrom());
        schedule.setValidUntil(request.getValidUntil());
    }

    // ----------------------------------------------------------------
    //  Acceso
    // ----------------------------------------------------------------

    /**
     * El grupo, por el repositorio del propio modulo. Si es de otro club, el
     * filtro y RLS hacen que no aparezca y sale como no encontrado, que es 404 y
     * no 403: quien pregunta no tiene por que enterarse de que existe.
     */
    private TrainingGroup grupoOException(UUID groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Grupo no encontrado"));
    }

    /**
     * El horario, <b>comprobando que cuelga de ese grupo</b>. Esa comprobacion
     * es lo unico que aisla la tabla entre clubes: sin {@code club_id} no hay
     * policy que la tape, asi que un {@code findById} pelado devolveria el
     * horario ajeno. El grupo de la ruta si esta filtrado, y por eso se carga
     * antes.
     */
    private GroupSchedule delGrupoOException(UUID groupId, UUID scheduleId) {
        grupoOException(groupId);
        return scheduleRepository.findById(scheduleId)
                .filter(horario -> horario.getTrainingGroup().getId().equals(groupId))
                .orElseThrow(() -> new EntityNotFoundException("Horario no encontrado"));
    }

    private GroupScheduleResponse toResponse(GroupSchedule schedule) {
        GroupScheduleResponse response = new GroupScheduleResponse();
        response.setId(schedule.getId());
        response.setGroupId(schedule.getTrainingGroup().getId());
        response.setGroupName(schedule.getTrainingGroup().getName());
        response.setDayOfWeek(schedule.getDayOfWeek());
        response.setStartTime(schedule.getStartTime());
        response.setEndTime(schedule.getEndTime());
        response.setModality(schedule.getModality());
        response.setValidFrom(schedule.getValidFrom());
        response.setValidUntil(schedule.getValidUntil());
        response.setInForce(schedule.isInForceOn(LocalDate.now()));
        return response;
    }
}
