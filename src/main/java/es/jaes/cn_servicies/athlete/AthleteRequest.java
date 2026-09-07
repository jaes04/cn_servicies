package es.jaes.cn_servicies.athlete;

import es.jaes.cn_servicies.guardian.AthleteGuardianRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AthleteRequest {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotNull
    private LocalDate birthDate;

    @NotBlank
    @Size(min = 9, max = 9, message = "El DNI debe tener exactamente 9 caracteres")
    @Pattern(regexp = "^[0-9]{8}[A-Z]$", message = "El DNI debe tener 8 dígitos y una letra mayúscula")
    private String dni;

    @NotNull
    private Gender gender;

    /**
     * Tutor y consentimientos. <b>Obligatorio si el atleta es menor de 14 anos</b>
     * (LOPDGDD art. 7); opcional por encima de esa edad, donde consiente el
     * mismo pero el club sigue queriendo el contacto de sus padres.
     *
     * <p>Que sea condicional no se puede expresar con Bean Validation sin un
     * validador propio, asi que la comprobacion vive en {@code AthleteService}.
     *
     * <p><b>Solo se acepta en el alta.</b> En la modificacion se rechaza: un
     * consentimiento no se edita, se otorga o se revoca.
     */
    @Valid
    private AthleteGuardianRequest guardian;
}