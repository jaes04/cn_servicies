package es.jaes.cn_servicies.guardian;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GuardianRequest {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    /**
     * Obligatorio, a diferencia del DNI del atleta: el tutor es mayor de edad y
     * tiene documento. Es ademas la clave por la que se reutiliza su ficha
     * cuando da de alta a un segundo hijo.
     */
    @NotBlank
    @Size(min = 9, max = 9, message = "El DNI debe tener exactamente 9 caracteres")
    @Pattern(regexp = "^[0-9]{8}[A-Z]$", message = "El DNI debe tener 8 dígitos y una letra mayúscula")
    private String dni;

    @NotBlank
    @Email
    private String email;

    private String phone;
}
