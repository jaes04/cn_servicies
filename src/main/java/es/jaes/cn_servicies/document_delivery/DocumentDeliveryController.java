package es.jaes.cn_servicies.document_delivery;

import es.jaes.cn_servicies.access.AccessGuard;
import es.jaes.cn_servicies.athlete.Athlete;
import es.jaes.cn_servicies.athlete.AthleteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Papeles entregados al club: registrar la entrega, ver el historial, ver si un
 * atleta tiene en regla lo que se le pide y si puede viajar.
 *
 * <p>Mismo reparto que en el certificado medico y por el mismo motivo: el
 * entrenador necesita saber si el nadador tiene la licencia y la documentacion
 * en regla, o si le falta el permiso para el viaje del sabado, pero el historial
 * con las fechas y quien anoto cada papel es del club. Anotar, corregir y borrar
 * la entrega si lo hace tambien el entrenador, porque es a el a quien le dan los
 * papeles en el vestuario. Y como en todo lo del entrenador, solo de los atletas
 * que hoy estan en sus grupos.
 */
@RestController
@RequestMapping("/api/document-deliveries")
@RequiredArgsConstructor
public class DocumentDeliveryController {

    private final DocumentDeliveryService deliveryService;
    private final AthleteService athleteService;
    private final AccessGuard accessGuard;

    /** Historial con fechas y quien lo anoto. Solo administradores. */
    @GetMapping("/athlete/{athleteId}")
    public ResponseEntity<List<DocumentDeliveryResponse>> history(@PathVariable UUID athleteId) {
        athleteService.findOrThrow(athleteId);
        return ResponseEntity.ok(deliveryService.historyForAthlete(athleteId));
    }

    /**
     * Si tiene hoy en regla la licencia de la temporada activa y el documento de
     * identidad. Una palabra por papel, sin fechas.
     */
    @GetMapping("/athlete/{athleteId}/status")
    public ResponseEntity<Map<DocumentDeliveryType, DocumentDeliveryStatus>> status(@PathVariable UUID athleteId) {
        accessGuard.requireAthleteAccess(athleteId);
        Athlete athlete = athleteService.findOrThrow(athleteId);
        return ResponseEntity.ok(deliveryService.statusForAthlete(athlete));
    }

    /** Si necesita permiso para viajar entre esas fechas, y si lo tiene. Las dos son obligatorias. */
    @GetMapping("/athlete/{athleteId}/travel-permit")
    public ResponseEntity<TravelPermitCoverageResponse> travelPermit(
            @PathVariable UUID athleteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        accessGuard.requireAthleteAccess(athleteId);
        Athlete athlete = athleteService.findOrThrow(athleteId);
        return ResponseEntity.ok(deliveryService.travelPermitCoverage(athlete, from, to));
    }

    @PostMapping("/athlete/{athleteId}")
    public ResponseEntity<DocumentDeliveryResponse> register(
            @PathVariable UUID athleteId,
            @Valid @RequestBody DocumentDeliveryRequest request,
            Principal principal) {

        accessGuard.requireAthleteAccess(athleteId);
        DocumentDelivery delivery = deliveryService.register(
                athleteService.findOrThrow(athleteId), request, principal.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(deliveryService.toResponse(delivery));
    }

    /**
     * Corrige una entrega mal anotada. Administrador, o entrenador si el atleta
     * de la entrega esta hoy en uno de sus grupos.
     *
     * <p>{@code {id}} solo acepta un UUID, por lo mismo que en los certificados:
     * sin el patron, un GET a cualquier ruta inexistente de esta rama
     * contestaria 405 en vez de 404.
     */
    @PutMapping("/{id:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}")
    public ResponseEntity<DocumentDeliveryResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentDeliveryRequest request,
            Principal principal) {

        accessGuard.requireDeliveryAccess(id);
        DocumentDelivery delivery = deliveryService.update(id, request, principal.getName());
        return ResponseEntity.ok(deliveryService.toResponse(delivery));
    }

    /** Borra una entrega anotada por error. Mismo reparto que corregirla. */
    @DeleteMapping("/{id:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        accessGuard.requireDeliveryAccess(id);
        deliveryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
