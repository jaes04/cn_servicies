package es.jaes.cn_servicies.user;

import es.jaes.cn_servicies.club.Club;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Que contrasenas se aceptan.
 *
 * <p>Sin contexto de Spring: la politica no necesita base de datos y asi las
 * reglas se leen de un vistazo, que es justo lo que hay que poder revisar
 * cuando alguien proponga cambiarlas.
 */
class PasswordPolicyTest {

    private static final String USUARIO = "entrenadora";
    private static final String CORREO = "entrenadora@club.local";

    private PasswordPolicy politica;
    private Club club;

    @BeforeEach
    void inicio() {
        politica = new PasswordPolicy(12, "");
        politica.cargarListas();

        club = new Club();
        club.setName("Club Natación Sierra Oeste");
        club.setSlug("sierra-oeste");
    }

    // ----------------------------------------------------------------
    //  1. Longitud
    // ----------------------------------------------------------------

    @Test
    @DisplayName("una contraseña larga y sin nada conocido dentro vale")
    void unaBuena() {
        assertThatCode(() -> comprobar("zanahoria-de-titanio-93")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("por debajo del mínimo se rechaza, y el mensaje dice cuántos faltan")
    void demasiadoCorta() {
        assertThatThrownBy(() -> comprobar("once-letras"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("12 caracteres");
    }

    @Test
    @DisplayName("justo en el mínimo vale")
    void justoEnElMinimo() {
        assertThatCode(() -> comprobar("mazapan12345")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("más de 72 bytes se rechaza, porque BCrypt tira lo que sobra sin avisar")
    void demasiadoLarga() {
        assertThatThrownBy(() -> comprobar("x".repeat(73)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72");
    }

    @Test
    @DisplayName("las letras con acento ocupan dos bytes y cuentan como dos")
    void elAcentoOcupaDos() {
        assertThatThrownBy(() -> comprobar("ñ".repeat(37)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72");
    }

    @Test
    @DisplayName("vacía o en blanco se rechaza")
    void vacia() {
        assertThatThrownBy(() -> comprobar("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> comprobar(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------
    //  2. Listas de contrasenas conocidas
    // ----------------------------------------------------------------

    @Test
    @DisplayName("una contraseña de la lista se rechaza")
    void deLaLista() {
        assertThatThrownBy(() -> comprobar("administrador123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("listas públicas");
    }

    @Test
    @DisplayName("disfrazarla con mayúsculas, acentos o guiones no la saca de la lista")
    void laListaSeComparaNormalizada() {
        assertThatThrownBy(() -> comprobar("Administrador-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("listas públicas");
        assertThatThrownBy(() -> comprobar("Contraseña.1234"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("listas públicas");
    }

    // ----------------------------------------------------------------
    //  3. Que no lleve dentro lo que cualquiera sabe de ti
    // ----------------------------------------------------------------

    @Test
    @DisplayName("no puede llevar dentro el nombre de usuario")
    void llevaElUsuario() {
        assertThatThrownBy(() -> comprobar("entrenadora-del-club-77"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usuario");
    }

    @Test
    @DisplayName("no puede llevar dentro la parte local del correo")
    void llevaElCorreo() {
        assertThatThrownBy(() -> politica.comprobar(
                "marisol.perez-9876", "otracosa", "marisol.perez@club.local", club))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correo");
    }

    @Test
    @DisplayName("no puede llevar dentro el nombre del club ni su slug")
    void llevaElClub() {
        assertThatThrownBy(() -> comprobar("cnsierraoeste2026"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("club");
        assertThatThrownBy(() -> comprobar("clubnatacionsierraoeste"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("club");
    }

    @Test
    @DisplayName("un usuario de menos de cuatro letras no bloquea media contraseña")
    void usuarioDemasiadoCortoParaBuscarlo() {
        assertThatCode(() -> politica.comprobar("zanahoria-de-titanio", "ana", "ana@club.local", club))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sin club no revienta: el alta pública todavía no lo sabe")
    void sinClub() {
        assertThatCode(() -> politica.comprobar("zanahoria-de-titanio", USUARIO, CORREO, null))
                .doesNotThrowAnyException();
    }

    // ----------------------------------------------------------------
    //  4. Alargarla repitiendo no cuenta
    // ----------------------------------------------------------------

    @Test
    @DisplayName("la misma letra doce veces no vale")
    void unaLetraRepetida() {
        assertThatThrownBy(() -> comprobar("kkkkkkkkkkkkkk"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repetida");
    }

    @Test
    @DisplayName("un trozo corto repetido tampoco")
    void unTrozoRepetido() {
        assertThatThrownBy(() -> comprobar("abcabcabcabcabc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repetida");
    }

    // ----------------------------------------------------------------
    //  5. Lo que NO se exige
    // ----------------------------------------------------------------

    /**
     * La regla de "mayuscula, numero y simbolo" produce {@code Password1!}, que
     * es corta, esta en todas las listas y encima hay que apuntarla en un papel.
     * Se exige longitud y que no sea conocida, y nada mas.
     */
    @Test
    @DisplayName("no se exige mayúscula, ni número, ni símbolo")
    void sinComposicionForzada() {
        assertThatCode(() -> comprobar("caballo correcto bateria grapa"))
                .doesNotThrowAnyException();
    }

    // ----------------------------------------------------------------

    private void comprobar(String password) {
        politica.comprobar(password, USUARIO, CORREO, club);
    }
}
