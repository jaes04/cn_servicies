package es.jaes.cn_servicies.guardian;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Correccion de la ficha de un tutor. Sin documento: no se cambia (ver
 * {@code GuardianService.update}).
 */
@Data
public class GuardianUpdateRequest {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotBlank
    @Email
    private String email;

    private String phone;
}
