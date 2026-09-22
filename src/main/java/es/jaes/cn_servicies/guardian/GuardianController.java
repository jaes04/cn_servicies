package es.jaes.cn_servicies.guardian;

import es.jaes.cn_servicies.access.AccessGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/**
 * Consulta y correccion de los tutores del club. Se dan de alta siempre desde un
 * atleta: en el alta del atleta, o despues en
 * {@code POST /api/athletes/{id}/guardians}. Un tutor sin atleta no tiene
 * finalidad por la que guardar sus datos.
 *
 * <p>Lo ven el administrador y el entrenador: en muchos clubes el entrenador
 * hace tambien el trabajo administrativo y es quien llama a las familias. Pero
 * el entrenador, igual que en el listado de atletas, solo ve a los tutores de
 * los atletas que llega a ver (tarea S.3.3.b): sin eso, este listado le daria
 * los nombres y el contacto de todo el club por la puerta de atras.
 */
@RestController
@RequestMapping("/api/guardians")
@RequiredArgsConstructor
public class GuardianController {

    private final GuardianService guardianService;
    private final AccessGuard accessGuard;

    @GetMapping
    public ResponseEntity<Page<GuardianResponse>> findAll(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {
        Set<UUID> soloEstos = accessGuard.seesWholeClub() ? null : accessGuard.visibleAthleteIds();
        return ResponseEntity.ok(guardianService.findAll(q, pageable, soloEstos));
    }

    /** Corrige nombre y contacto. El entrenador, solo de los tutores de sus atletas. */
    @PutMapping("/{id}")
    public ResponseEntity<GuardianResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody GuardianUpdateRequest request) {
        accessGuard.requireGuardianAccess(id);
        Set<UUID> soloEstos = accessGuard.seesWholeClub() ? null : accessGuard.visibleAthleteIds();
        return ResponseEntity.ok(guardianService.update(id, request, soloEstos));
    }
}
