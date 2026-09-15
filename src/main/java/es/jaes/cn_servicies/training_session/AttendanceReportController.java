package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.access.AccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Informes de asistencia.
 *
 * <p>Rama propia porque no son operaciones sobre una sesion: son preguntas sobre
 * un rango. El rango es obligatorio en las tres — un informe sin acotar sobre un
 * club con varias temporadas no es un informe, es un volcado.
 *
 * <p>Desde la tarea S.3.3.b el entrenador solo consulta y exporta los de los
 * grupos que lleva, y los de los atletas que hoy estan en ellos.
 */
@Slf4j
@RestController
@RequestMapping("/api/reports/attendance")
@RequiredArgsConstructor
public class AttendanceReportController {

    private final AttendanceReportService reportService;
    private final AttendanceCsvWriter csvWriter;
    private final AccessGuard accessGuard;

    /**
     * Suma todos los grupos del atleta: natacion y preparacion fisica cuentan igual.
     *
     * <p>Tambien para un entrenador que solo lleva uno de los dos. Es el dato de
     * asistencia del nadador, no del grupo, y no lleva nada que no sea numeros.
     */
    @GetMapping("/athlete/{athleteId}")
    public ResponseEntity<AthleteAttendanceResponse> forAthlete(
            @PathVariable UUID athleteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        accessGuard.requireAthleteAccess(athleteId);
        return ResponseEntity.ok(reportService.forAthlete(athleteId, from, to));
    }

    /**
     * Lo que queda pendiente de registrar, para pintarlo como aviso. Sin rango,
     * los ultimos 30 dias. Un entrenador ve lo de sus grupos, no lo del club.
     */
    @GetMapping("/pending")
    public ResponseEntity<PendingAttendanceResponse> pending(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Set<UUID> soloEstos = accessGuard.seesWholeClub() ? null : accessGuard.visibleGroupIds();
        return ResponseEntity.ok(reportService.pending(from, to, soloEstos));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<GroupAttendanceResponse> forGroup(
            @PathVariable UUID groupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        accessGuard.requireGroupAccess(groupId);
        return ResponseEntity.ok(reportService.forGroup(groupId, from, to));
    }

    /**
     * El mismo informe de grupo, en CSV descargable.
     *
     * <p><b>Lleva nombre y numeros, y nada mas</b>: ni DNI ni fecha de
     * nacimiento. Un CSV sale del sistema y ya no vuelve —se reenvia, se queda en
     * una carpeta compartida, acaba en un correo—, asi que lo que no salga aqui
     * es lo unico que seguro no acaba en ningun sitio. Ver {@code docs/rgpd.md} §3.
     *
     * <p>Se deja rastro en el log de <b>quien</b> exporto y <b>que rango</b>, sin
     * ningun dato de atletas: si algun dia hay que responder de donde salio un
     * listado, esa linea es la respuesta. Es lo minimo mientras no exista la
     * auditoria de la S.6.
     */
    @GetMapping("/group/{groupId}/export")
    public ResponseEntity<Resource> exportGroup(
            @PathVariable UUID groupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        accessGuard.requireGroupAccess(groupId);

        GroupAttendanceResponse informe = reportService.forGroup(groupId, from, to);
        byte[] csv = csvWriter.write(informe).getBytes(StandardCharsets.UTF_8);

        log.info("Exportación de asistencia: usuario={} grupo={} rango={}..{} filas={}",
                SecurityContextHolder.getContext().getAuthentication().getName(),
                groupId, from, to, informe.getAthletes().size());

        String nombre = "asistencia-" + informe.getGroupName().replaceAll("[^a-zA-Z0-9]+", "-")
                + "-" + from + "_" + to + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nombre + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(new ByteArrayResource(csv));
    }

    /**
     * Quien lleva varias sesiones seguidas sin aparecer. Sin {@code threshold},
     * tres.
     */
    @GetMapping("/group/{groupId}/gaps")
    public ResponseEntity<List<AttendanceGapResponse>> gaps(
            @PathVariable UUID groupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer threshold) {
        accessGuard.requireGroupAccess(groupId);
        return ResponseEntity.ok(reportService.gaps(groupId, from, to, threshold));
    }
}
