package es.jaes.cn_servicies.training_session;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Informes de asistencia.
 *
 * <p>Rama propia porque no son operaciones sobre una sesion: son preguntas sobre
 * un rango. El rango es obligatorio en las tres — un informe sin acotar sobre un
 * club con varias temporadas no es un informe, es un volcado.
 */
@RestController
@RequestMapping("/api/reports/attendance")
@RequiredArgsConstructor
public class AttendanceReportController {

    private final AttendanceReportService reportService;

    /** Suma todos los grupos del atleta: natacion y preparacion fisica cuentan igual. */
    @GetMapping("/athlete/{athleteId}")
    public ResponseEntity<AthleteAttendanceResponse> forAthlete(
            @PathVariable UUID athleteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.forAthlete(athleteId, from, to));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<GroupAttendanceResponse> forGroup(
            @PathVariable UUID groupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.forGroup(groupId, from, to));
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
        return ResponseEntity.ok(reportService.gaps(groupId, from, to, threshold));
    }
}
