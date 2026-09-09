package es.jaes.cn_servicies.training_session;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * La asistencia de un atleta en un rango.
 *
 * <p>Los cinco contadores suman {@link #sessions}, que son las sesiones
 * <b>celebradas</b> en las que ese atleta pertenecia al grupo. Ni las
 * canceladas ni las futuras entran: faltar a un entrenamiento que no existio no
 * es faltar.
 */
@Data
public class AthleteAttendanceResponse {

    private UUID athleteId;
    private String athleteName;

    /** Celebradas y siendo miembro. Es el denominador. */
    private int sessions;

    private int present;
    private int late;
    private int absent;
    private int excused;

    /**
     * Celebradas en las que nadie le marco nada. <b>Cuentan como falta</b>, y a
     * la vez salen en {@link #incidents} para que se puedan corregir: el dato no
     * se infla, pero tampoco se esconde de donde viene.
     */
    private int unrecorded;

    /** Sobre 1: {@code (present + late) / sessions}. Nulo si no hubo sesiones. */
    private Double attendanceRate;

    /** Los dias sin registrar de este atleta, para ir a arreglarlos. */
    private List<AttendanceIncidentResponse> incidents;
}
