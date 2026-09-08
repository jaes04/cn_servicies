package es.jaes.cn_servicies.training_group;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Grupos de entrenamiento.
 *
 * <p>El conteo de atletas por grupo y los endpoints de composicion son la 1.4,
 * y necesitan la entidad de pertenencia de la 1.3. Aqui solo esta el grupo.
 */
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class TrainingGroupController {

    private final TrainingGroupService groupService;

    /** Sin {@code seasonId} devuelve los del club entero, que con varias temporadas es mucho ruido. */
    @GetMapping
    public ResponseEntity<List<TrainingGroupResponse>> findAll(
            @RequestParam(required = false) UUID seasonId) {
        return ResponseEntity.ok(groupService.findAll(seasonId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TrainingGroupResponse> findById(@PathVariable UUID id) {
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
