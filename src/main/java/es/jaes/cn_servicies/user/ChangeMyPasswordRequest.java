package es.jaes.cn_servicies.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Cambiar la contrasena propia.
 *
 * <p>Pide la actual aunque la peticion venga ya autenticada. Un token robado
 * —o una sesion abierta en un ordenador compartido— no debe bastar para
 * quedarse con la cuenta, y esto es lo unico que lo impide mientras no exista
 * lista de revocacion.
 */
@Data
public class ChangeMyPasswordRequest {

    @NotBlank(message = "Hay que enviar la contraseña actual")
    private String currentPassword;

    // La politica entera —longitud, listas y contexto— la comprueba
    // PasswordPolicy en el servicio, para que sea la misma en todas las vias.
    @NotBlank
    private String newPassword;
}
