package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.club.ClubService;
import es.jaes.cn_servicies.season.Season;
import es.jaes.cn_servicies.tenant.TenantContext;
import es.jaes.cn_servicies.training_group.GroupSchedule;
import es.jaes.cn_servicies.training_group.GroupScheduleService;
import es.jaes.cn_servicies.training_group.TrainingGroup;
import es.jaes.cn_servicies.training_group.TrainingGroupService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Sesiones de entrenamiento (tarea 2.2).
 *
 * <p>El generador es la pieza con mas trampas del proyecto y todas se reducen a
 * una: <b>tiene que poder ejecutarse dos veces sin duplicar nada</b>. Un job lo
 * llamara periodicamente sobre rangos que se solapan, asi que la segunda pasada
 * sobre las mismas seis semanas es el caso normal, no el excepcional.
 *
 * <p>Los horarios los pide a {@code GroupScheduleService} y el grupo a
 * {@code TrainingGroupService}: este modulo no toca los repositorios del otro.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TrainingSessionService {

    /**
     * Tope duro del rango. Generar diez años de golpe son cientos de miles de
     * filas por un cero de mas en una fecha, y no hay ningun caso real que
     * necesite mas de una temporada.
     */
    private static final int MAXIMO_DIAS = 400;

    private final TrainingSessionRepository sessionRepository;
    private final TrainingGroupService groupService;
    private final GroupScheduleService scheduleService;
    private final ClubService clubService;

    /**
     * Materializa en sesiones los horarios del grupo dentro del rango.
     *
     * <p><b>Idempotente.</b> Lo que ya existe no se toca —ni se actualiza ni se
     * duplica—, y eso incluye lo cancelado: una sesion que alguien anulo no
     * puede resucitar porque el job vuelva a pasar por esa semana. El indice
     * unico {@code (schedule_id, session_date)} es la red por si se llega por
     * otra via; el mecanismo es este conjunto de pares que ya estan.
     *
     * <p>Solo genera donde el horario esta en vigor, y la vigencia ya quedo
     * acotada a la temporada en la 2.1: no hace falta volver a mirarla aqui.
     */
    public SessionGenerationResponse generate(UUID groupId, LocalDate from, LocalDate to) {
        validarRango(from, to);

        TrainingGroup group = groupService.findOrThrow(groupId);
        Club club = clubService.getById(TenantContext.require());

        List<GroupSchedule> horarios = scheduleService.inForceBetween(groupId, from, to);
        Set<Hueco> yaEstan = huecosOcupados(groupId, from, to);

        List<TrainingSession> nuevas = new ArrayList<>();
        int existentes = 0;

        for (LocalDate dia = from; !dia.isAfter(to); dia = dia.plusDays(1)) {
            for (GroupSchedule horario : horarios) {
                if (horario.getDayOfWeek() != dia.getDayOfWeek() || !horario.isInForceOn(dia)) {
                    continue;
                }
                if (yaEstan.contains(new Hueco(horario.getId(), dia))) {
                    existentes++;
                    continue;
                }
                nuevas.add(materializar(club, group, horario, dia));
            }
        }

        sessionRepository.saveAll(nuevas);

        SessionGenerationResponse resumen = new SessionGenerationResponse();
        resumen.setFrom(from);
        resumen.setTo(to);
        resumen.setCreated(nuevas.size());
        resumen.setAlreadyExisted(existentes);
        return resumen;
    }

    /**
     * Sesion suelta, sin horario detras: una competicion, un entrenamiento
     * extra, un cambio de piscina puntual.
     *
     * <p>Su {@code schedule} nulo la deja fuera de la idempotencia del
     * generador, que es lo correcto: no sale de ningun horario, asi que ningun
     * horario puede reponerla ni considerarla un duplicado.
     */
    public TrainingSessionResponse createOneOff(UUID groupId, TrainingSessionRequest request) {
        TrainingGroup group = groupService.findOrThrow(groupId);
        Club club = clubService.getById(TenantContext.require());

        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new IllegalArgumentException(
                    "La hora de fin tiene que ser posterior a la de inicio");
        }

        // Fuera de la temporada del grupo la sesion no cuelga de nada: ni
        // aparece en sus listados ni cuenta en sus informes.
        Season season = group.getSeason();
        if (!season.covers(request.getDate())) {
            throw new IllegalArgumentException(
                    "La fecha cae fuera de la temporada del grupo");
        }

        TrainingSession session = new TrainingSession();
        session.setClub(club);
        session.setTrainingGroup(group);
        session.setDate(request.getDate());
        session.setStartTime(request.getStartTime());
        session.setEndTime(request.getEndTime());
        session.setModality(request.getModality());
        session.setStatus(SessionStatus.SCHEDULED);

        return toResponse(sessionRepository.save(session));
    }

    /**
     * Cancela una sesion con su motivo. <b>La fila no se borra</b>: el hueco en
     * el calendario tiene que quedar explicado, y en la 2.3 de la sesion colgara
     * la asistencia.
     *
     * <p>Se permite cancelar una sesion pasada: cuando el club se entera de que
     * el 12 de marzo no hubo piscina, el 12 de marzo ya paso.
     */
    public TrainingSessionResponse cancel(UUID sessionId, CancellationReason reason) {
        TrainingSession session = findOrThrow(sessionId);

        if (session.isCancelled()) {
            throw new IllegalArgumentException("La sesión ya estaba cancelada");
        }

        session.setStatus(SessionStatus.CANCELLED);
        session.setCancellationReason(reason);
        return toResponse(sessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public List<TrainingSessionResponse> findAll(UUID groupId, LocalDate from, LocalDate to) {
        groupService.findOrThrow(groupId);
        validarRango(from, to);
        return sessionRepository.findByGroupBetween(groupId, from, to).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TrainingSessionResponse findById(UUID sessionId) {
        return toResponse(findOrThrow(sessionId));
    }

    /**
     * Por clave primaria y sin comprobar el grupo, que es lo que permite que la
     * ruta sea {@code /api/sessions/{id}} y no una de cuatro segmentos.
     *
     * <p><b>Lo unico que impide que devuelva la sesion de otro club es la policy
     * de RLS</b> —el filtro de Hibernate no se aplica a las cargas por id—, y por
     * eso esta tabla lleva {@code club_id} aunque sea hija. Si algun dia alguien
     * quita la policy, esto se convierte en un agujero silencioso.
     */
    public TrainingSession findOrThrow(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Sesión no encontrada"));
    }

    // ----------------------------------------------------------------
    //  Interioridades del generador
    // ----------------------------------------------------------------

    /** Un sitio ya ocupado: ese horario, ese dia. */
    private record Hueco(UUID scheduleId, LocalDate date) {}

    private Set<Hueco> huecosOcupados(UUID groupId, LocalDate from, LocalDate to) {
        Set<Hueco> ocupados = new HashSet<>();
        for (Object[] fila : sessionRepository.findGeneratedKeysBetween(groupId, from, to)) {
            ocupados.add(new Hueco((UUID) fila[0], (LocalDate) fila[1]));
        }
        return ocupados;
    }

    /**
     * La sesion copia la hora y la modalidad del horario en vez de leerlas por
     * la relacion. Si el horario cambia en marzo, lo de febrero tiene que seguir
     * diciendo la hora a la que se entreno de verdad.
     */
    private TrainingSession materializar(Club club, TrainingGroup group,
                                         GroupSchedule horario, LocalDate dia) {
        TrainingSession session = new TrainingSession();
        session.setClub(club);
        session.setTrainingGroup(group);
        session.setSchedule(horario);
        session.setDate(dia);
        session.setStartTime(horario.getStartTime());
        session.setEndTime(horario.getEndTime());
        session.setModality(horario.getModality());
        session.setStatus(SessionStatus.SCHEDULED);
        return session;
    }

    private void validarRango(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Hacen falta las dos fechas del rango");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("El fin del rango es anterior al inicio");
        }
        if (from.plusDays(MAXIMO_DIAS).isBefore(to)) {
            throw new IllegalArgumentException(
                    "El rango no puede pasar de " + MAXIMO_DIAS + " días");
        }
    }

    private TrainingSessionResponse toResponse(TrainingSession session) {
        TrainingSessionResponse response = new TrainingSessionResponse();
        response.setId(session.getId());
        response.setGroupId(session.getTrainingGroup().getId());
        response.setGroupName(session.getTrainingGroup().getName());
        response.setDate(session.getDate());
        response.setStartTime(session.getStartTime());
        response.setEndTime(session.getEndTime());
        response.setModality(session.getModality());
        response.setStatus(session.getStatus());
        response.setCancellationReason(session.getCancellationReason());
        response.setOneOff(session.isOneOff());
        return response;
    }
}
