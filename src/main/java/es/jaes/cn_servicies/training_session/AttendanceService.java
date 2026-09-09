package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.training_group.AthleteGroup;
import es.jaes.cn_servicies.training_group.AthleteGroupService;
import es.jaes.cn_servicies.user.User;
import es.jaes.cn_servicies.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pasar lista (tarea 2.3).
 *
 * <p>La composicion del grupo la pide a {@code AthleteGroupService}: este modulo
 * no toca el repositorio de pertenencias. Y la pide <b>a la fecha de la
 * sesion</b>, no a hoy, que es la diferencia entre pasar lista bien y pasarla
 * con la plantilla actual de un entrenamiento de hace tres semanas.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final TrainingSessionService sessionService;
    private final AthleteGroupService membershipService;
    private final UserService userService;

    /**
     * La lista de una sesion: quien pertenecia al grupo ese dia y como asistio,
     * si ya se registro.
     */
    @Transactional(readOnly = true)
    public RosterResponse roster(UUID sessionId) {
        TrainingSession session = sessionService.findOrThrow(sessionId);
        return construirRoster(session);
    }

    /**
     * Guarda la lista en lote.
     *
     * <p><b>Entera o nada</b>, como el alta de atletas de la 1.4: si uno de la
     * lista no pertenecia al grupo ese dia, no entra ninguno. Media lista deja al
     * club sin saber que se registro.
     *
     * <p>No hace falta mandar a todos: lo que no venga se queda como estaba.
     * Pasar lista es incremental —se marca a los que faltan y se sigue— y
     * obligar a mandar el grupo entero convertiria cada correccion en una
     * oportunidad de pisar lo que ya habia.
     */
    public RosterResponse save(UUID sessionId, AttendanceRequest request) {
        TrainingSession session = sessionService.findOrThrow(sessionId);

        if (session.isCancelled()) {
            throw new IllegalArgumentException(
                    "No se puede pasar lista de una sesión cancelada");
        }

        Set<UUID> miembros = miembrosEnLaFecha(session);
        User quienRegistra = usuarioActual();
        LocalDateTime ahora = LocalDateTime.now();

        // Se valida todo antes de escribir nada: la transaccion tambien lo
        // desharia, pero fallar antes de tocar la base da un mensaje mejor.
        for (AttendanceEntryRequest entrada : request.getEntries()) {
            if (!miembros.contains(entrada.getAthleteId())) {
                throw new IllegalArgumentException(
                        "Hay un atleta en la lista que no pertenecía al grupo ese día");
            }
        }

        for (AttendanceEntryRequest entrada : request.getEntries()) {
            attendanceRepository.upsert(
                    session.getId(),
                    entrada.getAthleteId(),
                    entrada.getStatus().name(),
                    quienRegistra.getId(),
                    ahora);
        }

        marcarRealizadaSiProcede(session);

        return construirRoster(session);
    }

    /**
     * Una sesion con la lista pasada esta hecha. Lo pone el guardado y no un
     * boton aparte: dos estados que hay que mantener a mano acaban
     * desincronizados.
     *
     * <p><b>Salvo que la sesion sea futura.</b> Pasar lista por adelantado esta
     * permitido, pero marcarla realizada antes de que ocurra la contaria como
     * celebrada en los informes y —peor— la sacaria del alcance de la
     * regeneracion, que no toca las {@code DONE}: cambiar el horario dejaria de
     * arrastrarla. El estado espera a que llegue el dia.
     */
    private void marcarRealizadaSiProcede(TrainingSession session) {
        if (!session.getDate().isAfter(LocalDate.now())) {
            sessionService.markDone(session);
        }
    }

    /**
     * Los atletas del grupo <b>en la fecha de la sesion</b>. Por el servicio de
     * pertenencias, que es quien sabe responder eso desde la 1.3.
     */
    private Set<UUID> miembrosEnLaFecha(TrainingSession session) {
        return new HashSet<>(membersOn(session).stream().map(m -> m.getAthlete().getId()).toList());
    }

    private List<AthleteGroup> membersOn(TrainingSession session) {
        return membershipService.membersOn(
                session.getTrainingGroup().getId(), session.getDate());
    }

    private RosterResponse construirRoster(TrainingSession session) {
        Map<UUID, Attendance> registrado = new HashMap<>();
        for (Attendance a : attendanceRepository.findBySession(session.getId())) {
            registrado.put(a.getAthlete().getId(), a);
        }

        List<RosterEntryResponse> atletas = new ArrayList<>();
        for (AthleteGroup pertenencia : membersOn(session)) {
            RosterEntryResponse entrada = new RosterEntryResponse();
            entrada.setAthleteId(pertenencia.getAthlete().getId());
            entrada.setAthleteName(pertenencia.getAthlete().getFirstName()
                    + " " + pertenencia.getAthlete().getLastName());

            Attendance registro = registrado.get(pertenencia.getAthlete().getId());
            if (registro != null) {
                entrada.setStatus(registro.getStatus());
                if (registro.getRegisteredBy() != null) {
                    entrada.setRegisteredBy(registro.getRegisteredBy().getUsername());
                }
            }
            atletas.add(entrada);
        }

        RosterResponse response = new RosterResponse();
        response.setSessionId(session.getId());
        response.setDate(session.getDate());
        response.setStartTime(session.getStartTime());
        response.setEndTime(session.getEndTime());
        response.setModality(session.getModality());
        response.setStatus(session.getStatus());
        response.setGroupId(session.getTrainingGroup().getId());
        response.setGroupName(session.getTrainingGroup().getName());
        response.setAthletes(atletas);
        return response;
    }

    /** Quien esta pasando lista. Por el servicio de usuarios, no por su repositorio. */
    private User usuarioActual() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.findEntityByUsername(username);
    }
}
