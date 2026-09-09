package es.jaes.cn_servicies.training_session;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Lo que el club tiene pendiente de registrar (tarea 2.4).
 *
 * <p>Existe para que el entrenador se entere. Se consulta al entrar y se pinta
 * como aviso, en vez de mandar un correo que nadie abre o escribir en un log que
 * nadie lee.
 *
 * <p>Separa dos cosas que se ven parecidas y no lo son: una sesion a la que
 * <b>nadie paso lista</b> —falta el dia entero— y una en la que se paso pero
 * <b>quedaron atletas sin marcar</b>, que ya estan contando como falta en los
 * informes. La segunda es la que corre prisa, porque el dato malo ya esta en el
 * historial de alguien.
 */
@Data
public class PendingAttendanceResponse {

    private LocalDate from;
    private LocalDate to;

    /** Sesiones ya pasadas que siguen sin lista. */
    private List<PendingSessionResponse> sessionsWithoutRoster;

    /** Sesiones con lista a medias, con cuantos quedaron sin marcar. */
    private List<PendingSessionResponse> incompleteRosters;

    /** Las dos listas juntas, para pintar el numerito del aviso sin sumar en el cliente. */
    private int total;
}
