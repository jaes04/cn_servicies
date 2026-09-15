package es.jaes.cn_servicies.document_delivery;

import es.jaes.cn_servicies.access.AccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/**
 * Informe de documentacion pendiente (bloque 3c).
 *
 * <p>Cuelga de {@code /api/reports}, junto a los de asistencia, y cae bajo su misma
 * regla de {@code SecurityConfig}: lo consultan el administrador y el entrenador.
 * Al entrenador le llega acotado a sus grupos.
 *
 * <p>Sustituye a {@code /api/medical-certificates/expiring}, que se retiro en este
 * mismo bloque.
 */
@RestController
@RequestMapping("/api/reports/documents")
@RequiredArgsConstructor
public class PendingDocumentationController {

    private final PendingDocumentationService pendingService;
    private final AccessGuard accessGuard;

    @GetMapping("/pending")
    public ResponseEntity<PendingDocumentationResponse> pending() {
        Set<UUID> soloEstos = accessGuard.seesWholeClub() ? null : accessGuard.visibleGroupIds();
        return ResponseEntity.ok(pendingService.pending(soloEstos));
    }
}
