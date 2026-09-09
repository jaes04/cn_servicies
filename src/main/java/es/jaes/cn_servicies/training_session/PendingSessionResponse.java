package es.jaes.cn_servicies.training_session;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/** Una sesion que le falta algo por registrar. */
@Data
public class PendingSessionResponse {

    private UUID sessionId;
    private LocalDate date;
    private UUID groupId;
    private String groupName;

    /**
     * Cuantos atletas quedaron sin marcar. Nulo en las sesiones a las que no se
     * paso lista en absoluto: ahi no faltan tres nombres, falta la lista entera.
     */
    private Integer unrecorded;
}
