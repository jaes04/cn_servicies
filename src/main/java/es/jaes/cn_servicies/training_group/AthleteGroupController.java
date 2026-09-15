package es.jaes.cn_servicies.training_group;

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
 * Composicion de los grupos: quien esta, quien estuvo, y las altas y bajas.
 *
 * <p>Sin {@code @RequestMapping} de clase porque sirve dos ramas: lo que cuelga
 * del grupo y el historial que cuelga del atleta. La segunda vive aqui, y no en
 * {@code AthleteController}, porque es logica de pertenencia: el modulo que la
 * mantiene es este.
 */
@RestController
@RequiredArgsConstructor
public class AthleteGroupController {

    private final AthleteGroupService membershipService;
    private final AccessGuard accessGuard;

    /**
     * Miembros del grupo. Sin {@code date}, los de hoy; con ella, <b>los que
     * estaban ese dia</b>, que es lo que hace falta para pasar lista de una
     * sesion pasada y no lo mismo que la lista actual.
     */
    @GetMapping("/api/groups/{groupId}/athletes")
    public ResponseEntity<List<AthleteGroupResponse>> members(
            @PathVariable UUID groupId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        accessGuard.requireGroupAccess(groupId);
        return ResponseEntity.ok(membershipService.membersOnAsResponse(groupId, date));
    }

    /**
     * Alta de uno o de muchos: la lista de uno es el caso individual. Se aplica
     * entera o no se aplica.
     */
    @PostMapping("/api/groups/{groupId}/athletes")
    public ResponseEntity<List<AthleteGroupResponse>> assign(
            @PathVariable UUID groupId,
            @Valid @RequestBody AssignAthletesRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(membershipService.assignAll(groupId, request));
    }

    /**
     * Saca al atleta del grupo. <b>La fila no se borra</b>: se le pone fecha de
     * baja y se queda como historico de que estuvo ahi. El verbo describe lo que
     * pasa de puertas afuera —el atleta deja de estar en el grupo—, no lo que
     * ocurre en la tabla.
     *
     * <p>Sin {@code leftOn} la baja es hoy. El motivo es obligatorio: es un enum
     * corto y saber por que se fue alguien tiene valor para el club.
     */
    @DeleteMapping("/api/groups/{groupId}/athletes/{athleteId}")
    public ResponseEntity<AthleteGroupResponse> leave(
            @PathVariable UUID groupId,
            @PathVariable UUID athleteId,
            @RequestParam LeaveReason reason,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate leftOn) {
        return ResponseEntity.ok(
                membershipService.leaveGroup(groupId, athleteId, leftOn, reason));
    }

    /** Por que grupos ha pasado un atleta. Con {@code seasonId}, solo los de esa temporada. */
    @GetMapping("/api/athletes/{athleteId}/group-history")
    public ResponseEntity<List<AthleteGroupResponse>> history(
            @PathVariable UUID athleteId,
            @RequestParam(required = false) UUID seasonId) {
        accessGuard.requireAthleteAccess(athleteId);
        return ResponseEntity.ok(membershipService.historyAsResponse(athleteId, seasonId));
    }
}
