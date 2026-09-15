package es.jaes.cn_servicies.athlete;

import es.jaes.cn_servicies.guardian.AthleteGuardianRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    /**
     * Documento de identidad: DNI, NIE o pasaporte. <b>Opcional</b> desde el
     * bloque 3b.
     *
     * <p>Hasta entonces era obligatorio y solo admitia el formato del DNI, asi que
     * un nadador extranjero no se podia dar de alta y un menor sin DNI obligaba a
     * inventarse uno. Ahora se aceptan letras y numeros, entre 5 y 20, que cubre
     * los tres documentos. Lo que se pierde es comprobar la forma exacta de un DNI:
     * un pasaporte no tiene una forma exacta que comprobar.
     *
     * <p>Se admiten minusculas y espacios alrededor: el servicio lo guarda sin
     * espacios y en mayusculas, para que {@code x1234567l} y {@code X1234567L} sean
     * el mismo documento.
     */
    @Pattern(regexp = "^\\s*$|^\\s*[A-Za-z0-9]{5,20}\\s*$",
            message = "El documento de identidad admite solo letras y números, entre 5 y 20")
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
    private AthleteGuardianRequest guardianConsent;
}
