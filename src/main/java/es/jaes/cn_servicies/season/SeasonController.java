package es.jaes.cn_servicies.season;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Temporadas del club.
 *
 * <p><b>No hay borrado, y es deliberado.</b> El historico por temporada es el
 * motivo de que esta entidad exista: de ella colgaran los grupos y la
 * asistencia, y borrar una se llevaria por delante lo que se queria conservar.
 * Una temporada terminada se queda con {@code active = false}.
 */
@RestController
@RequestMapping("/api/seasons")
@RequiredArgsConstructor
public class SeasonController {

    private final SeasonService seasonService;

    @GetMapping
    public ResponseEntity<List<SeasonResponse>> findAll() {
        return ResponseEntity.ok(seasonService.findAll());
    }

    /** La temporada en curso. 404 si el club no ha activado ninguna todavia. */
    @GetMapping("/active")
    public ResponseEntity<SeasonResponse> active() {
        return ResponseEntity.ok(seasonService.findActive());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SeasonResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(seasonService.findById(id));
    }

    @PostMapping
    public ResponseEntity<SeasonResponse> create(@Valid @RequestBody SeasonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(seasonService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SeasonResponse> update(@PathVariable UUID id,
                                                 @Valid @RequestBody SeasonRequest request) {
        return ResponseEntity.ok(seasonService.update(id, request));
    }

    /**
     * Marca esta como la temporada en curso y apaga la anterior.
     *
     * <p>Endpoint propio y no un campo del PUT: activar tiene un efecto sobre
     * otra fila, y eso no debe poder ocurrir de rebote al editar unas fechas.
     */
    @PostMapping("/{id}/activation")
    public ResponseEntity<SeasonResponse> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(seasonService.activate(id));
    }
}
