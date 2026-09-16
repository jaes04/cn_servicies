package es.jaes.cn_servicies.auth;

import es.jaes.cn_servicies.user.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Set;

@Data
public class SignupWithRoleRequest {

    @NotBlank
    private String username;

    @Email
    @NotBlank
    private String email;

    // Como en el resto de altas: la politica la comprueba PasswordPolicy en el
    // servicio. Esta anotacion se quedo atras al moverla, y daba el error en
    // errors.password —con el minimo viejo de 8— en vez de en message.
    @NotBlank
    private String password;

    @NotEmpty(message = "Debe indicar al menos un rol")
    private Set<RoleName> roles;
}