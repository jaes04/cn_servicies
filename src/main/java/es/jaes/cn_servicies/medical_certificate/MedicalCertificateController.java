package es.jaes.cn_servicies.medical_certificate;

import es.jaes.cn_servicies.access.AccessGuard;
import es.jaes.cn_servicies.athlete.AthleteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Certificados medicos: registrarlos, ver el estado de un atleta y saber cuales
 * caducan pronto.
 *
 * <p>Mismo reparto que en los consentimientos y por el mismo motivo: el
 * entrenador necesita saber si un nadador esta cubierto antes de que entre al
 * agua, pero las fechas concretas y quien valido el papel son del club.
 */
@RestController
@RequestMapping("/api/medical-certificates")
@RequiredArgsConstructor
public class MedicalCertificateController {

    private final MedicalCertificateService certificateService;
    private final AthleteService athleteService;
    private final AccessGuard accessGuard;

    /** Historial con fechas y validador. */
    @GetMapping("/athlete/{athleteId}")
    public ResponseEntity<List<MedicalCertificateResponse>> history(@PathVariable UUID athleteId) {
        athleteService.findOrThrow(athleteId);
        return ResponseEntity.ok(certificateService.historyForAthlete(athleteId));
    }

    /**
     * Si esta cubierto hoy. Una sola palabra: {@code VALID}, {@code EXPIRING_SOON},
     * {@code EXPIRED} o {@code MISSING}.
     *
     * <p>Desde la tarea S.3.3.b, el entrenador solo lo consulta de los atletas que
     * hoy estan en sus grupos.
     */
    @GetMapping("/athlete/{athleteId}/status")
    public ResponseEntity<Map<String, MedicalCertificateStatus>> status(@PathVariable UUID athleteId) {
        accessGuard.requireAthleteAccess(athleteId);
        return ResponseEntity.ok(Map.of("status", certificateService.statusForAthlete(athleteId)));
    }

    @PostMapping("/athlete/{athleteId}")
    public ResponseEntity<MedicalCertificateResponse> register(
            @PathVariable UUID athleteId,
            @Valid @RequestBody MedicalCertificateRequest request,
            Principal principal) {

        MedicalCertificate certificate = certificateService.register(
                athleteService.findOrThrow(athleteId), request, principal.getName());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(certificateService.toResponse(certificate));
    }

    /**
     * Los que caducan en los proximos dias, el mas urgente primero. Es lo que el
     * club mira para reclamar los certificados que faltan, y sustituye al aviso
     * automatico mientras no haya envio de correo.
     */
    @GetMapping("/expiring")
    public ResponseEntity<List<MedicalCertificateResponse>> expiring(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(certificateService.expiringWithin(days));
    }
}
