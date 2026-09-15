package es.jaes.cn_servicies.guardian;

import es.jaes.cn_servicies.access.AccessGuard;
import es.jaes.cn_servicies.athlete.AthleteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consentimientos posteriores al alta: consultarlos, registrar uno nuevo y
 * revocarlo.
 *
 * <p>Ruta propia y no anidada bajo {@code /api/athletes/**} a proposito: esa
 * rama esta cerrada en bloque a ADMIN y TECHNICAL_STAFF, y aqui hacen falta
 * permisos distintos para el historial y para el estado. Sigue el patron de
 * {@code /api/athlete-documents/athlete/**}.
 *
 * <p>El atleta se resuelve por {@code AthleteService}, asi que uno de otro club
 * da 404 y no 403: existe, pero no para quien pregunta.
 */
@RestController
@RequestMapping("/api/consents")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;
    private final AthleteService athleteService;
    private final AccessGuard accessGuard;

    /** Historial completo, con la evidencia de cada decision. Solo administradores. */
    @GetMapping("/athlete/{athleteId}")
    public ResponseEntity<List<ConsentResponse>> history(@PathVariable UUID athleteId) {
        athleteService.findOrThrow(athleteId);
        return ResponseEntity.ok(consentService.historyForAthlete(athleteId));
    }

    /**
     * Que ampara hoy cada finalidad. Es lo que necesita un entrenador antes de
     * publicar una foto, y no le hace falta saber quien firmo ni cuando.
     *
     * <p>Desde la tarea S.3.3.b, solo de los atletas que hoy estan en sus grupos.
     */
    @GetMapping("/athlete/{athleteId}/status")
    public ResponseEntity<Map<ConsentType, Boolean>> status(@PathVariable UUID athleteId) {
        accessGuard.requireAthleteAccess(athleteId);
        return ResponseEntity.ok(consentService.statusForAthlete(athleteId));
    }

    /**
     * Registra una decision nueva. No modifica ninguna anterior: cambiar de
     * opinion es revocar la vigente y registrar otra.
     *
     * <p>La IP solo se conserva si la evidencia es {@code ONLINE_FORM}. Hasta
     * que exista un portal donde el tutor conteste el mismo, quien llama aqui es
     * siempre personal del club, asi que esa evidencia <b>no debe usarse desde
     * este endpoint</b>: registraria la IP de quien teclea, no la de quien
     * consiente.
     */
    @PostMapping("/athlete/{athleteId}")
    public ResponseEntity<ConsentResponse> record(@PathVariable UUID athleteId,
                                                  @Valid @RequestBody ConsentRequest request,
                                                  HttpServletRequest http) {
        Consent consent = consentService.record(
                athleteService.findOrThrow(athleteId), request, http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(consentService.toResponse(consent));
    }

    /**
     * Revoca un consentimiento vigente.
     *
     * <p><b>No es un DELETE, y no por capricho.</b> La fila no se borra: se le
     * pone fecha de revocacion y se queda como prueba de que existio. Un DELETE
     * en la ruta daria a entender lo contrario a quien lea la API.
     */
    @PostMapping("/{consentId}/revocation")
    public ResponseEntity<ConsentResponse> revoke(@PathVariable UUID consentId) {
        return ResponseEntity.ok(consentService.toResponse(consentService.revoke(consentId)));
    }
}
