package es.jaes.cn_servicies.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El contador de intentos fallidos de login.
 *
 * <p>Va con un reloj de mentira en lugar de esperas de verdad: las reglas que
 * importan —cuando caduca la ventana, cuanto dura el bloqueo, cuanto dura el
 * siguiente— son todas de tiempo, y con {@code Thread.sleep} serian lentas y
 * flojas.
 */
class LoginAttemptServiceTest {

    private static final String IP = "10.0.0.7";
    private static final int POR_USUARIO = 3;
    private static final int POR_IP = 5;
    private static final Duration VENTANA = Duration.ofMinutes(15);
    private static final Duration BLOQUEO = Duration.ofMinutes(5);
    private static final Duration TOPE = Duration.ofMinutes(20);

    private RelojMovil reloj;
    private LoginAttemptService servicio;

    @BeforeEach
    void inicio() {
        reloj = new RelojMovil();
        servicio = new LoginAttemptService(POR_USUARIO, POR_IP, VENTANA, BLOQUEO, TOPE, reloj);
    }

    // ----------------------------------------------------------------
    //  1. El limite por cuenta
    // ----------------------------------------------------------------

    @Test
    @DisplayName("por debajo del límite no bloquea")
    void porDebajoDelLimite() {
        fallar("ana", POR_USUARIO - 1);

        assertThatCode(() -> servicio.comprobar("ana", IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("al agotar los intentos bloquea esa cuenta")
    void alAgotarLosIntentos() {
        fallar("ana", POR_USUARIO);

        assertThatThrownBy(() -> servicio.comprobar("ana", IP))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .hasMessageContaining("esta cuenta");
    }

    @Test
    @DisplayName("bloquear una cuenta no bloquea a las demás")
    void soloLaCuentaQueFallo() {
        fallar("ana", POR_USUARIO);

        assertThatCode(() -> servicio.comprobar("bruno", IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el mismo usuario con otras mayúsculas o con espacios es el mismo contador")
    void elUsuarioSeNormaliza() {
        fallar("ana", POR_USUARIO - 1);
        servicio.anotarFallo("  ANA  ", IP);

        assertThatThrownBy(() -> servicio.comprobar("ana", IP))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    // ----------------------------------------------------------------
    //  2. El acierto y la ventana
    // ----------------------------------------------------------------

    @Test
    @DisplayName("un acierto borra los fallos acumulados")
    void elAciertoBorraElContador() {
        fallar("ana", POR_USUARIO - 1);
        servicio.anotarAcierto("ana", IP);
        fallar("ana", POR_USUARIO - 1);

        assertThatCode(() -> servicio.comprobar("ana", IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("los fallos sueltos y espaciados no llegan a bloquear: la ventana los olvida")
    void laVentanaCaduca() {
        for (int i = 0; i < POR_USUARIO * 3; i++) {
            servicio.anotarFallo("ana", IP);
            reloj.avanzar(VENTANA.plusMinutes(1));
        }

        assertThatCode(() -> servicio.comprobar("ana", IP)).doesNotThrowAnyException();
    }

    // ----------------------------------------------------------------
    //  3. El bloqueo: cuanto dura y cuanto dura el siguiente
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el bloqueo se levanta solo al pasar el tiempo")
    void elBloqueoSeLevanta() {
        fallar("ana", POR_USUARIO);
        reloj.avanzar(BLOQUEO.plusSeconds(1));

        assertThatCode(() -> servicio.comprobar("ana", IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el bloqueo dura exactamente lo configurado: un segundo antes sigue cerrado")
    void unSegundoAntesSigueCerrado() {
        fallar("ana", POR_USUARIO);
        reloj.avanzar(BLOQUEO.minusSeconds(1));

        assertThatThrownBy(() -> servicio.comprobar("ana", IP))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    @DisplayName("el segundo bloqueo dura el doble que el primero")
    void elBloqueoEsProgresivo() {
        fallar("ana", POR_USUARIO);
        reloj.avanzar(BLOQUEO.plusSeconds(1));
        fallar("ana", POR_USUARIO);

        assertThat(esperaPendiente("ana")).isEqualTo(BLOQUEO.multipliedBy(2));
    }

    @Test
    @DisplayName("esperar a que caduque la ventana no rebaja el siguiente bloqueo")
    void esperarNoRebajaElBloqueo() {
        fallar("ana", POR_USUARIO);
        reloj.avanzar(VENTANA.plus(TOPE).plusMinutes(1));
        fallar("ana", POR_USUARIO);

        assertThat(esperaPendiente("ana")).isEqualTo(BLOQUEO.multipliedBy(2));
    }

    @Test
    @DisplayName("el bloqueo no crece más allá del tope")
    void elBloqueoTieneTope() {
        for (int vuelta = 0; vuelta < 6; vuelta++) {
            fallar("ana", POR_USUARIO);
            reloj.avanzar(TOPE.plusSeconds(1));
        }
        fallar("ana", POR_USUARIO);

        assertThat(esperaPendiente("ana")).isEqualTo(TOPE);
    }

    // ----------------------------------------------------------------
    //  4. El limite por IP
    // ----------------------------------------------------------------

    @Test
    @DisplayName("probar una contraseña en muchas cuentas distintas acaba bloqueando la conexión")
    void elLimitePorIp() {
        for (int i = 0; i < POR_IP; i++) {
            servicio.anotarFallo("usuario" + i, IP);
        }

        assertThatThrownBy(() -> servicio.comprobar("otro_mas", IP))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .hasMessageContaining("esta conexión");
    }

    @Test
    @DisplayName("bloquear una conexión no bloquea a las demás")
    void soloLaIpQueFallo() {
        for (int i = 0; i < POR_IP; i++) {
            servicio.anotarFallo("usuario" + i, IP);
        }

        assertThatCode(() -> servicio.comprobar("ana", "10.0.0.8")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un acierto desde esa conexión también la desbloquea: el club comparte el wifi de la piscina")
    void elAciertoBorraLaCuentaDeLaIp() {
        for (int i = 0; i < POR_IP - 1; i++) {
            servicio.anotarFallo("usuario" + i, IP);
        }
        servicio.anotarAcierto("entrenadora", IP);
        for (int i = 0; i < POR_IP - 1; i++) {
            servicio.anotarFallo("otro" + i, IP);
        }

        assertThatCode(() -> servicio.comprobar("ana", IP)).doesNotThrowAnyException();
    }

    // ----------------------------------------------------------------
    //  5. Lo que ve la pantalla
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el error dice cuánto falta para poder reintentar")
    void elErrorDiceCuantoFalta() {
        fallar("ana", POR_USUARIO);
        reloj.avanzar(Duration.ofMinutes(2));

        TooManyLoginAttemptsException error = capturar("ana");

        assertThat(error.getRetryAfter()).isEqualTo(Duration.ofMinutes(3));
        assertThat(error.getMessage()).contains("3 minutos");
    }

    @Test
    @DisplayName("cuando queda menos de un minuto lo dice en segundos")
    void enSegundosCuandoQuedaPoco() {
        fallar("ana", POR_USUARIO);
        reloj.avanzar(BLOQUEO.minusSeconds(30));

        assertThat(capturar("ana").getMessage()).contains("30 segundos");
    }

    @Test
    @DisplayName("olvidarTodo deja el contador como recién arrancado")
    void olvidarTodo() {
        fallar("ana", POR_USUARIO);
        servicio.olvidarTodo();

        assertThatCode(() -> servicio.comprobar("ana", IP)).doesNotThrowAnyException();
    }

    // ----------------------------------------------------------------

    private void fallar(String usuario, int veces) {
        for (int i = 0; i < veces; i++) {
            servicio.anotarFallo(usuario, IP);
        }
    }

    private TooManyLoginAttemptsException capturar(String usuario) {
        try {
            servicio.comprobar(usuario, IP);
        } catch (TooManyLoginAttemptsException e) {
            return e;
        }
        throw new AssertionError("Se esperaba que " + usuario + " estuviera bloqueado");
    }

    private Duration esperaPendiente(String usuario) {
        return capturar(usuario).getRetryAfter();
    }

    /** Un reloj que solo se mueve cuando se le dice. */
    private static final class RelojMovil extends Clock {

        private Instant ahora = Instant.parse("2026-09-16T10:00:00Z");

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
