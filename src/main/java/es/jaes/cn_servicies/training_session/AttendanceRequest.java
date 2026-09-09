package es.jaes.cn_servicies.training_session;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * La lista pasada de una sesion, en una sola peticion.
 *
 * <p><b>No hace falta que vengan todos los atletas del grupo.</b> Pasar lista es
 * incremental: el entrenador marca a los que faltan y sigue. Lo que no venga se
 * queda como estaba.
 */
@Data
public class AttendanceRequest {

    @NotEmpty(message = "La lista no puede venir vacía")
    @Valid
    private List<AttendanceEntryRequest> entries;
}
