package es.jaes.cn_servicies.athlete;

import es.jaes.cn_servicies.access.AccessGuard;
import es.jaes.cn_servicies.guardian.GuardianLinkRequest;
import es.jaes.cn_servicies.guardian.GuardianResponse;
import es.jaes.cn_servicies.guardian.GuardianService;
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
 * Fichas de atleta.
 *
 * <p>Desde la tarea S.3.3.b un entrenador solo llega a los atletas que <b>hoy</b>
 * estan en alguno de los grupos que lleva. Sigue pudiendo dar de alta — decision
 * del club —, pero la ficha nueva no la vera hasta que el administrador la meta
 * en uno de sus grupos.
 */
@RestController
@RequestMapping("/api/athletes")
@RequiredArgsConstructor
public class AthleteController {

    private final AthleteService athleteService;
    private final GuardianService guardianService;
    private final AccessGuard accessGuard;

    @GetMapping
    public ResponseEntity<Page<AthleteResponse>> findAll(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Gender gender,
            @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {
        Set<UUID> soloEstos = accessGuard.seesWholeClub() ? null : accessGuard.visibleAthleteIds();
        return ResponseEntity.ok(athleteService.findAll(q, gender, pageable, soloEstos));
    }

    @GetMapping("/my-tutees")
    public ResponseEntity<List<AthleteResponse>> myTutees(Principal principal) {
        return ResponseEntity.ok(athleteService.findTuteesByUser(principal.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AthleteResponse> findById(@PathVariable UUID id) {
        accessGuard.requireAthleteAccess(id);
        return ResponseEntity.ok(athleteService.findById(id));
    }

    @PostMapping
    public ResponseEntity<AthleteResponse> create(@Valid @RequestBody AthleteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(athleteService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AthleteResponse> update(@PathVariable UUID id, @Valid @RequestBody AthleteRequest request) {
        accessGuard.requireAthleteAccess(id);
        return ResponseEntity.ok(athleteService.update(id, request));
    }

    /**
     * Da de alta un tutor para este atleta y los vincula. Si ya existe un tutor
     * con ese documento en el club, se reutiliza. Los consentimientos van
     * aparte, por {@code /api/consents}.
     */
    @PostMapping("/{id}/guardians")
    public ResponseEntity<GuardianResponse> addGuardian(@PathVariable UUID id,
                                                        @Valid @RequestBody GuardianLinkRequest request) {
        accessGuard.requireAthleteAccess(id);
        Set<UUID> soloEstos = accessGuard.seesWholeClub() ? null : accessGuard.visibleAthleteIds();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(guardianService.addToAthlete(athleteService.findOrThrow(id), request, soloEstos));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        athleteService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
