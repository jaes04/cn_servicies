package es.jaes.cn_servicies.competition_result;

import es.jaes.cn_servicies.access.AccessGuard;
import es.jaes.cn_servicies.athlete.Gender;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Marcas de competicion.
 *
 * <p>Desde la tarea S.3.3.b un entrenador solo ve y registra las de los atletas
 * que hoy estan en sus grupos.
 */
@RestController
@RequestMapping("/api/competition-results")
@RequiredArgsConstructor
public class CompetitionResultController {

    private final CompetitionResultService resultService;
    private final AccessGuard accessGuard;

    @GetMapping
    public ResponseEntity<Page<CompetitionResultResponse>> findAll(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Stroke stroke,
            @RequestParam(required = false) Integer distanceMeters,
            @RequestParam(required = false) Integer poolLength,
            @RequestParam(required = false) Boolean partial,
            @RequestParam(required = false) Gender gender,
            @PageableDefault(size = 20, sort = "competitionDate") Pageable pageable) {
        Set<UUID> soloEstos = accessGuard.seesWholeClub() ? null : accessGuard.visibleAthleteIds();
        return ResponseEntity.ok(resultService.findAll(
                q, stroke, distanceMeters, poolLength, partial, gender, pageable, soloEstos));
    }

    @GetMapping("/me")
    public ResponseEntity<Page<CompetitionResultResponse>> findMine(
            @RequestParam(required = false) Stroke stroke,
            @RequestParam(required = false) Integer distanceMeters,
            @RequestParam(required = false) Integer poolLength,
            @RequestParam(required = false) Boolean partial,
            @RequestParam(required = false) Gender gender,
            @PageableDefault(size = 20, sort = "competitionDate") Pageable pageable,
            Principal principal) {
        return ResponseEntity.ok(resultService.findByCurrentUser(
                principal.getName(), stroke, distanceMeters, poolLength, partial, gender, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompetitionResultResponse> findById(@PathVariable UUID id) {
        accessGuard.requireResultAccess(id);
        return ResponseEntity.ok(resultService.findById(id));
    }

    @GetMapping("/athlete/{athleteId}")
    public ResponseEntity<List<CompetitionResultResponse>> findByAthlete(@PathVariable UUID athleteId) {
        accessGuard.requireAthleteAccess(athleteId);
        return ResponseEntity.ok(resultService.findByAthlete(athleteId));
    }

    @PostMapping
    public ResponseEntity<CompetitionResultResponse> create(@Valid @RequestBody CompetitionResultRequest request) {
        accessGuard.requireAthleteAccess(request.getAthleteId());
        return ResponseEntity.status(HttpStatus.CREATED).body(resultService.create(request));
    }

    /**
     * Las dos comprobaciones hacen falta: la del resultado, para no editar uno
     * ajeno, y la del cuerpo, para no mover uno propio a un atleta que no le toca.
     */
    @PutMapping("/{id}")
    public ResponseEntity<CompetitionResultResponse> update(@PathVariable UUID id, @Valid @RequestBody CompetitionResultRequest request) {
        accessGuard.requireResultAccess(id);
        accessGuard.requireAthleteAccess(request.getAthleteId());
        return ResponseEntity.ok(resultService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        resultService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
