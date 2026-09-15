package es.jaes.cn_servicies.athlete;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Formato del documento de identidad en la peticion (bloque 3b), sin base de
 * datos.
 *
 * <p>Va aparte porque la validacion del DTO no la ejecuta el servicio: la hace el
 * controlador con {@code @Valid}, y un test de servicio pasaria por encima sin
 * mirarla.
 */
class AthleteRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest(name = "\"{0}\" es válido")
    @NullSource
    @ValueSource(strings = {
            "12345678Z",     // DNI
            "X1234567L",     // NIE
            "x1234567l",     // NIE en minúsculas: el servicio lo normaliza
            " 12345678Z ",   // con espacios alrededor
            "PAA123456",     // pasaporte español
            "AB12345",       // pasaporte extranjero corto
            "",              // vacío: se guarda como nulo
            "   "
    })
    @DisplayName("se aceptan DNI, NIE, pasaporte, y el documento vacío")
    void validos(String documento) {
        assertThat(validator.validateProperty(conDocumento(documento), "dni")).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" no es válido")
    @ValueSource(strings = {
            "1234",                     // demasiado corto
            "123456789012345678901",    // 21: demasiado largo
            "12345678-Z",               // con guion
            "ÑA123456",                 // fuera de A-Z
            "12 345 678"                // espacios dentro
    })
    @DisplayName("se rechaza lo que no son letras y números entre 5 y 20")
    void invalidos(String documento) {
        assertThat(validator.validateProperty(conDocumento(documento), "dni")).isNotEmpty();
    }

    private AthleteRequest conDocumento(String documento) {
        AthleteRequest request = new AthleteRequest();
        request.setDni(documento);
        return request;
    }
}
