package es.jaes.cn_servicies.training_session;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Un atleta que lleva varias sesiones seguidas sin aparecer.
 *
 * <p>Es un indicador para que el club pregunte, y conviene tener claro lo que
 * <b>no</b> hace: no avisa a nadie, no marca al atleta y no guarda nada. Es una
 * consulta. Que el sistema escriba solo a una familia es otra decision, y de las
 * que hay que pensar despacio.
 */
@Data
public class AttendanceGapResponse {

    private UUID athleteId;
    private String athleteName;

    /** Sesiones celebradas seguidas sin asistir, contando desde la mas reciente. */
    private int consecutiveAbsences;

    /** Ultimo dia que si vino, dentro del rango consultado. Nulo si no vino ninguno. */
    private LocalDate lastAttendedOn;
}
