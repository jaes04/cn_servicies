package es.jaes.cn_servicies.training_session;

import lombok.Data;

import java.util.UUID;

/**
 * Un atleta en la lista de una sesion.
 *
 * <p><b>Solo id y nombre.</b> Ni DNI, ni fecha de nacimiento, ni foto: para
 * pasar lista no hacen falta, y este es el endpoint que va a consumir el movil.
 * Es la regla de minimizacion de {@code docs/rgpd.md} §3, y el mismo criterio
 * que ya sigue {@code AthleteGroupResponse}.
 */
@Data
public class RosterEntryResponse {

    private UUID athleteId;
    private String athleteName;

    /** Nulo si a ese atleta todavia no se le ha pasado lista en esta sesion. */
    private AttendanceStatus status;

    /** Quien lo registro, por su nombre de usuario. Nulo si aun no hay registro. */
    private String registeredBy;
}
