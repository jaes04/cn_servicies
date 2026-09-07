package es.jaes.cn_servicies.season;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * No lleva {@code active}: activar una temporada es una accion aparte, con su
 * propio endpoint, porque apaga la que estuviera encendida. Colarla en el alta
 * significaria que crear una temporada futura desactiva la actual sin querer.
 */
@Data
public class SeasonRequest {

    @NotBlank
    @Size(max = 50)
    private String name;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;
}
