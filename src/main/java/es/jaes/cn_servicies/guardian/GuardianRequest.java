package es.jaes.cn_servicies.guardian;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class GuardianRequest {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    /**
     * Documento de identidad del tutor: DNI, NIE o pasaporte. <b>Obligatorio</b>, a
     * diferencia del documento del atleta: el tutor es mayor de edad, firma el
     * consentimiento, y su documento es la clave por la que se reutiliza su ficha
     * cuando da de alta a un segundo hijo.
     *
     * <p>Hasta el bloque 3c solo admitia el formato del DNI, asi que un tutor con NIE
     * o pasaporte no podia registrarse ni, por tanto, dar de alta a su hijo menor de
     * 14. Mismo formato que el del atleta: letras y numeros, entre 5 y 20. Se guarda
     * sin espacios y en mayusculas.
     */
    @NotBlank(message = "El documento de identidad del tutor es obligatorio")
    @Pattern(regexp = "^\\s*[A-Za-z0-9]{5,20}\\s*$",
            message = "El documento de identidad admite solo letras y números, entre 5 y 20")
    private String dni;

    @NotBlank
    @Email
    private String email;

    private String phone;
}
