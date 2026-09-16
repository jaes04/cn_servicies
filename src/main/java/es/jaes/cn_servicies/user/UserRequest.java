package es.jaes.cn_servicies.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

@Data
public class UserRequest {

    @NotBlank
    private String username;

    @Email
    @NotBlank
    private String email;

    // La longitud y el resto de la politica los comprueba PasswordPolicy, en el
    // servicio: es el unico sitio por el que pasan todas las contrasenas,
    // incluidas las que no vienen de un DTO. Aqui solo que llegue algo.
    @NotBlank
    private String password;

    private Set<RoleName> roles;
}
