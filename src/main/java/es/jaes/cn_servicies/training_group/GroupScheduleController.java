package es.jaes.cn_servicies.training_group;

import es.jaes.cn_servicies.training_session.ScheduleChangeService;
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
 * Horarios recurrentes de un grupo (tarea 2.1).
 *
 * <p><b>Todas las rutas cuelgan del grupo</b>, y no es solo estetica de REST:
 * {@code group_schedules} es tabla hija y no tiene policy de RLS, asi que un
 * {@code /api/schedules/{id}} plano daria acceso al horario de otro club. El id
 * del grupo en la ruta es lo que permite comprobar la pertenencia contra una
 * tabla que si esta aislada.
 *
 * <p><b>Sin cambios en {@code SecurityConfig}</b>: caen bajo las reglas de
 * {@code /api/groups/**} que ya existen. El entrenador consulta; montar el
 * horario es del club, igual que montar el grupo.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/schedules")
@RequiredArgsConstructor
public class GroupScheduleController {

    private final GroupScheduleService scheduleService;

    /**
     * La edicion y el borrado pasan por aqui: cambiar un horario arrastra sus
     * sesiones futuras, y las dos cosas tienen que ir en la misma transaccion.
     */
    private final ScheduleChangeService scheduleChangeService;

    /**
     * Sin {@code date}, todos los horarios vivos del grupo; con ella, los que
     * estaban en vigor ese dia. Lo segundo es lo que hara falta para explicar
     * por que existio una sesion de hace tres meses.
     */
    @GetMapping
    public ResponseEntity<List<GroupScheduleResponse>> findAll(
            @PathVariable UUID groupId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(scheduleService.findAll(groupId, date));
    }

    @PostMapping
    public ResponseEntity<GroupScheduleResponse> create(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scheduleService.create(groupId, request));
    }

    /**
     * Edita el horario <b>y rehace las sesiones futuras</b> que salian de el.
     *
     * <p>Va por {@code ScheduleChangeService} y no por el servicio de horarios a
     * secas porque las dos cosas tienen que ocurrir en la misma transaccion: si
     * el cambio se guardara y la regeneracion fallara despues, el calendario se
     * quedaria describiendo un horario que ya no existe.
     */
    @PutMapping("/{scheduleId}")
    public ResponseEntity<GroupScheduleResponse> update(
            @PathVariable UUID groupId,
            @PathVariable UUID scheduleId,
            @Valid @RequestBody GroupScheduleRequest request) {
        return ResponseEntity.ok(
                scheduleChangeService.updateAndRegenerate(groupId, scheduleId, request));
    }

    /**
     * Borrado logico: la fila se queda, porque las sesiones que ya ocurrieron
     * cuelgan de ella y tienen que poder explicar de donde salieron.
     *
     * <p>Lo que si se va son sus <b>sesiones futuras</b>: un horario borrado no
     * puede seguir poniendo entrenamientos en el calendario de las proximas
     * semanas.
     */
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID groupId,
            @PathVariable UUID scheduleId) {
        scheduleChangeService.deleteAndDiscard(groupId, scheduleId);
        return ResponseEntity.noContent().build();
    }
}
