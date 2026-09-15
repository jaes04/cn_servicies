package es.jaes.cn_servicies.guardian;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Formato del documento de identidad del tutor, sin base de datos.
 *
 * <p>Mismo formato que el del atleta, con una diferencia que es la que hay que
 * proteger: <b>aqui es obligatorio</b>. El tutor firma el consentimiento, y su
 * documento es lo que evita crear una ficha nueva con cada hijo.
 */
class GuardianRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest(name = "\"{0}\" es válido")
    @ValueSource(strings = {"12345678Z", "X1234567L", "x1234567l", " 12345678Z ", "PAA123456"})
    @DisplayName("se aceptan DNI, NIE y pasaporte")
    void validos(String documento) {
        assertThat(validator.validateProperty(conDocumento(documento), "dni")).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" no es válido")
    @NullSource
    @ValueSource(strings = {"", "   ", "1234", "12345678-Z", "ÑA123456", "123456789012345678901"})
    @DisplayName("se rechaza el documento vacío, y lo que no son letras y números entre 5 y 20")
    void invalidos(String documento) {
        assertThat(validator.validateProperty(conDocumento(documento), "dni")).isNotEmpty();
    }

    private GuardianRequest conDocumento(String documento) {
        GuardianRequest request = new GuardianRequest();
        request.setDni(documento);
        return request;
    }
}
