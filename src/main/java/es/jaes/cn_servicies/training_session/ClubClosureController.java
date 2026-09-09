package es.jaes.cn_servicies.training_session;

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
 * Calendario de excepciones del club: festivos y cierres de piscina.
 *
 * <p>Cuelga del club y no de un grupo porque un festivo lo es para todos. Lo que
 * si acota el cierre es la modalidad: cerrar la piscina no tiene por que tumbar
 * el entrenamiento del gimnasio.
 */
@RestController
@RequestMapping("/api/closures")
@RequiredArgsConstructor
public class ClubClosureController {

    private final ClubClosureService closureService;

    @GetMapping
    public ResponseEntity<List<ClubClosureResponse>> findAll(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(closureService.findAll(from, to));
    }

    /**
     * Declara el cierre y cancela lo que ya estuviera generado dentro de el.
     * La respuesta trae {@code cancelledSessions}: cuantos entrenamientos se ha
     * llevado por delante, que es lo que hay que mirar antes de irse.
     */
    @PostMapping
    public ResponseEntity<ClubClosureResponse> create(
            @Valid @RequestBody ClubClosureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(closureService.create(request));
    }

    /**
     * Quita el cierre del calendario. <b>No reactiva las sesiones que tumbo</b>:
     * eso se hace una a una, porque nadie puede saber cuales cayeron por este
     * cierre y cuales las canceló alguien a mano ese mismo día.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        closureService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
