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
 * Certificados medicos: registrarlos y ver el estado de un atleta.
 *
 * <p>Mismo reparto que en los consentimientos y por el mismo motivo: el
 * entrenador necesita saber si un nadador esta cubierto antes de que entre al
 * agua, pero las fechas concretas y quien valido el papel son del club.
 *
 * <p>La lista de los que caducan pronto ({@code /expiring}) se retiro en el bloque
 * 3c. Con el certificado por temporada se llenaba de golpe al final del curso, y
 * la sustituye el informe de documentacion pendiente, en
 * {@code /api/reports/documents/pending}.
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
     * Corrige la fecha de emision o la temporada de un certificado mal anotado.
     * Solo administradores, como el alta.
     *
     * <p><b>{@code {id}} solo acepta un UUID</b>, y no es un adorno. Sin el patron,
     * esta ruta y la de borrar se comerian cualquier segmento suelto, y un GET a
     * una ruta que no existe —la retirada {@code /expiring}, sin ir mas lejos—
     * contestaria 405 "metodo no permitido" en vez de 404: el frontend lo leeria
     * como que la ruta existe. Lo pillo el test de {@code /expiring}.
     */
    @PutMapping("/{id:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}")
    public ResponseEntity<MedicalCertificateResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody MedicalCertificateRequest request,
            Principal principal) {

        MedicalCertificate certificate = certificateService.update(id, request, principal.getName());
        return ResponseEntity.ok(certificateService.toResponse(certificate));
    }

    /** Borra un certificado anotado por error. Solo administradores. */
    @DeleteMapping("/{id:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        certificateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
