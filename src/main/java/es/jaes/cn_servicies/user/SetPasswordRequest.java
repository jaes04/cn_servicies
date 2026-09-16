package es.jaes.cn_servicies.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * El administrador fija la contrasena de una cuenta.
 *
 * <p>No pide la actual: existe justamente para cuando nadie la sabe. Es la
 * respuesta a una contrasena olvidada mientras no haya recuperacion por correo.
 */
@Data
public class SetPasswordRequest {

    @NotBlank
    private String password;
}
