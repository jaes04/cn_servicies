package es.jaes.cn_servicies.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    // El minimo es el mismo que en el alta de usuario. La politica de
    // contrasenas de la S.3.1 —minimo mas largo y contraste con listas
    // filtradas— sigue pendiente y subira los dos a la vez.
    @NotBlank
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    private String newPassword;
}
