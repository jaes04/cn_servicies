package es.jaes.cn_servicies.training_session;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Algo que el entrenador tiene que ir a mirar.
 *
 * <p>Dos formas distintas de quedarse sin dato, y conviene no mezclarlas: una
 * sesion a la que <b>no se paso lista en absoluto</b>, y un atleta al que
 * <b>nadie marco</b> en una sesion en la que si se paso. La primera no cuenta
 * como falta de nadie; la segunda si, y por eso hay que poder corregirla.
 */
@Data
public class AttendanceIncidentResponse {

    private UUID sessionId;
    private LocalDate date;

    /** Nulo en las incidencias de sesion, que no son de ningun atleta en concreto. */
    private UUID athleteId;
    private String athleteName;
}
