package es.jaes.cn_servicies.athlete;

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
}