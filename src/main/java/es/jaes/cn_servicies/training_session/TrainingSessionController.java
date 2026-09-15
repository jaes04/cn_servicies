package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.access.AccessGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sesiones de entrenamiento.
 *
 * <p>Sin {@code @RequestMapping} de clase porque sirve dos ramas. Lo que se hace
 * <b>sobre el calendario de un grupo</b> —listar, generar, añadir una suelta—
 * cuelga del grupo. Lo que se hace <b>sobre una sesion concreta</b> cuelga de
 * {@code /api/sessions/{id}}, con el id suelto.
 *
 * <p>Esa segunda rama es la que obligo a que la tabla lleve {@code club_id}: un
 * id suelto se resuelve por clave primaria, donde el filtro de Hibernate no
 * llega. Lo que la tapa es la policy de RLS. La 2.3 colgara de aqui el
 * {@code /roster} que consume el movil.
 *
 * <p>Desde la tarea S.3.3.b el entrenador solo alcanza las sesiones de los
 * grupos que lleva. RLS dice "de tu club"; el guardian dice "de tus grupos".
 */
@RestController
@RequiredArgsConstructor
public class TrainingSessionController {

    private final TrainingSessionService sessionService;
    private final AccessGuard accessGuard;

    /** El calendario del grupo en un rango. Las dos fechas son obligatorias. */
    @GetMapping("/api/groups/{groupId}/sessions")
    public ResponseEntity<List<TrainingSessionResponse>> findAll(
            @PathVariable UUID groupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        accessGuard.requireGroupAccess(groupId);
        return ResponseEntity.ok(sessionService.findAll(groupId, from, to));
    }

    /**
     * Materializa los horarios del grupo en sesiones.
     *
     * <p>Ruta propia y no un efecto de otra cosa: crea N filas de golpe.
     * <b>Se puede lanzar las veces que haga falta</b> —lo que ya existe no se
     * toca— y la respuesta dice cuantas se crearon y cuantas ya estaban.
     */
    @PostMapping("/api/groups/{groupId}/sessions/generation")
    public ResponseEntity<SessionGenerationResponse> generate(
            @PathVariable UUID groupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(sessionService.generate(groupId, from, to));
    }

    /** Sesion suelta, fuera de horario: competicion, extra, recuperacion. */
    @PostMapping("/api/groups/{groupId}/sessions")
    public ResponseEntity<TrainingSessionResponse> createOneOff(
            @PathVariable UUID groupId,
            @Valid @RequestBody TrainingSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sessionService.createOneOff(groupId, request));
    }

    @GetMapping("/api/sessions/{sessionId}")
    public ResponseEntity<TrainingSessionResponse> findById(@PathVariable UUID sessionId) {
        accessGuard.requireSessionAccess(sessionId);
        return ResponseEntity.ok(sessionService.findById(sessionId));
    }

    /**
     * Cancela la sesion. <b>No la borra</b>: el hueco en el calendario tiene que
     * quedar explicado, y de la sesion colgara la asistencia.
     *
     * <p>Ruta propia y no un {@code PUT} del estado: cancelar es lo unico que se
     * puede hacer con el estado desde fuera —el paso a DONE lo hace la 2.3 al
     * pasar lista— y un campo editable invitaria a marcarla hecha a mano.
     */
    @PostMapping("/api/sessions/{sessionId}/cancellation")
    public ResponseEntity<TrainingSessionResponse> cancel(
            @PathVariable UUID sessionId,
            @RequestParam CancellationReason reason) {
        accessGuard.requireSessionAccess(sessionId);
        return ResponseEntity.ok(sessionService.cancel(sessionId, reason));
    }

    /**
     * Deshace una cancelacion.
     *
     * <p>Existe porque cancelar dejo de ser cosa de una persona: declarar un
     * cierre con las fechas mal tumba veinte entrenamientos de golpe. Lo puede
     * el entrenador, igual que cancelar — quien se equivoca tiene que poder
     * arreglarlo sin esperar al administrador.
     */
    @PostMapping("/api/sessions/{sessionId}/reactivation")
    public ResponseEntity<TrainingSessionResponse> reactivate(@PathVariable UUID sessionId) {
        accessGuard.requireSessionAccess(sessionId);
        return ResponseEntity.ok(sessionService.reactivate(sessionId));
    }
}
