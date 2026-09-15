package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.access.AccessGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Pasar lista.
 *
 * <p>Las dos rutas cuelgan de la sesion y devuelven <b>lo mismo</b>: el roster
 * completo. Guardar contesta con la lista ya actualizada para que el movil no
 * tenga que volver a pedirla — al borde de una piscina, cada llamada de mas es
 * una oportunidad de quedarse a medias.
 *
 * <p>Desde la tarea S.3.3.b solo pasa lista quien lleva el grupo, como principal
 * o como ayudante. Antes lo podia cualquier entrenador del club, y era la unica
 * escritura de datos de menores que no pedia ninguna relacion con ellos.
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AccessGuard accessGuard;

    /**
     * Los atletas que pertenecian al grupo <b>el dia de la sesion</b>, con su
     * estado si ya se registro. Una sola llamada, pensada para el movil.
     */
    @GetMapping("/roster")
    public ResponseEntity<RosterResponse> roster(@PathVariable UUID sessionId) {
        accessGuard.requireSessionAccess(sessionId);
        return ResponseEntity.ok(attendanceService.roster(sessionId));
    }

    /**
     * Guarda la lista en lote. No hace falta mandar a todos: lo que no venga se
     * queda como estaba.
     *
     * <p>{@code PUT} y no {@code POST} porque es idempotente: mandar dos veces lo
     * mismo deja el mismo estado, que es justo lo que hace falta cuando la
     * conexion se cae a mitad y el movil reintenta.
     */
    @PutMapping("/attendance")
    public ResponseEntity<RosterResponse> save(
            @PathVariable UUID sessionId,
            @Valid @RequestBody AttendanceRequest request) {
        accessGuard.requireSessionAccess(sessionId);
        return ResponseEntity.ok(attendanceService.save(sessionId, request));
    }
}
