package es.jaes.cn_servicies.training_group;

import es.jaes.cn_servicies.access.AccessGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Grupos de entrenamiento.
 *
 * <p>El conteo de atletas por grupo y los endpoints de composicion son la 1.4,
 * y necesitan la entidad de pertenencia de la 1.3. Aqui solo esta el grupo.
 *
 * <p>Desde la tarea S.3.3.b <b>el entrenador solo ve los grupos que lleva</b>.
 * Crear, editar, borrar y duplicar siguen siendo del club, por
 * {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class TrainingGroupController {

    private final TrainingGroupService groupService;
    private final AccessGuard accessGuard;

    /**
     * Sin {@code seasonId} devuelve los del club entero, que con varias temporadas
     * es mucho ruido. Un entrenador recibe solo los suyos: es su pantalla de "mis
     * grupos".
     */
    @GetMapping
    public ResponseEntity<List<TrainingGroupResponse>> findAll(
            @RequestParam(required = false) UUID seasonId) {
        Set<UUID> soloEstos = accessGuard.seesWholeClub() ? null : accessGuard.visibleGroupIds();
        return ResponseEntity.ok(groupService.findAll(seasonId, soloEstos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TrainingGroupResponse> findById(@PathVariable UUID id) {
        accessGuard.requireGroupAccess(id);
        return ResponseEntity.ok(groupService.findById(id));
    }

    @PostMapping
    public ResponseEntity<TrainingGroupResponse> create(
            @Valid @RequestBody TrainingGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TrainingGroupResponse> update(
            @PathVariable UUID id, @Valid @RequestBody TrainingGroupRequest request) {
        return ResponseEntity.ok(groupService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        groupService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Copia los grupos de una temporada a otra. Ruta propia y no un parametro
     * del alta: crea N filas de golpe y conviene que se lea como lo que es.
     */
    @PostMapping("/duplication")
    public ResponseEntity<List<TrainingGroupResponse>> duplicate(
            @Valid @RequestBody DuplicateGroupsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.duplicate(request));
    }
}
