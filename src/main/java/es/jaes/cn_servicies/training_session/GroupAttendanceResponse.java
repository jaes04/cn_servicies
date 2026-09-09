package es.jaes.cn_servicies.training_session;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** La asistencia de un grupo entero en un rango, atleta a atleta. */
@Data
public class GroupAttendanceResponse {

    private UUID groupId;
    private String groupName;
    private LocalDate from;
    private LocalDate to;

    /** Sesiones celebradas del grupo en el rango. */
    private int sessions;

    /** Media del grupo, sobre 1. Nula si no hubo sesiones celebradas. */
    private Double attendanceRate;

    private List<AthleteAttendanceResponse> athletes;

    /**
     * Sesiones ya pasadas a las que nadie paso lista.
     *
     * <p>No cuentan como falta de nadie y no entran en ningun denominador, pero
     * salen aqui porque son la razon mas probable de que un informe parezca
     * incompleto. Mientras esta lista no este vacia, el resto del informe se lee
     * sabiendo que faltan dias por registrar.
     */
    private List<AttendanceIncidentResponse> sessionsWithoutRoster;
}
