package es.jaes.cn_servicies.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cuenta los intentos fallidos de login y cierra la puerta un rato cuando se
 * pasan de la raya.
 *
 * <p>Lleva dos cuentas separadas y cualquiera de las dos bloquea:
 *
 * <ul>
 *   <li><b>Por cuenta</b>, estricta. Es la que protege de verdad: quien prueba
 *       contrasenas contra una cuenta concreta se queda sin intentos enseguida,
 *       venga de donde venga.
 *   <li><b>Por IP</b>, holgada. Es la red de seguridad contra quien prueba una
 *       contrasena en muchas cuentas distintas, que a la cuenta por usuario se
 *       le escapa porque nunca repite objetivo.
 * </ul>
 *
 * <p><b>Un acierto borra las dos cuentas.</b> Sin eso, un club entero detras del
 * wifi de la piscina comparte IP y los despistes de unos dejarian fuera a los
 * demas.
 *
 * <p><b>El bloqueo es progresivo</b>: cada vez que se vuelve a agotar los
 * intentos, la espera se dobla hasta el tope. Un despiste cuesta unos minutos;
 * insistir cuesta una hora.
 *
 * <p><b>Vive en memoria y se pierde al reiniciar</b>, y cada instancia lleva la
 * suya. Es suficiente para un despliegue de una sola instancia —el de la beta— y
 * evita meter una dependencia nueva. El dia que haya dos instancias detras de un
 * balanceador, el limite efectivo se multiplica por el numero de instancias y
 * esto tendra que pasar a un almacen compartido.
 *
 * <p><b>La clave del usuario se guarda resumida, nunca en claro.</b> En un login
 * fallido el username no es de fiar: la gente teclea la contrasena en el campo
 * de usuario mas de lo que parece, y con el resumen un volcado de memoria no la
 * deja a la vista. Solo hace falta comparar, no leer.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    /** A partir de aqui se limpian las entradas caducadas. Sin esto, un ataque largo hace crecer el mapa sin fin. */
    private static final int TAMANO_PARA_LIMPIAR = 10_000;

    private final int maximoPorUsuario;
    private final int maximoPorIp;
    private final Duration ventana;
    private final Duration bloqueoInicial;
    private final Duration bloqueoMaximo;
    private final Clock reloj;

    private final Map<String, Intentos> intentos = new ConcurrentHashMap<>();

    // Explicito porque hay dos constructores y Spring no tiene por que adivinar
    // cual es el suyo: el otro es el de los tests.
    @Autowired
    public LoginAttemptService(
            @Value("${app.login.max-attempts-per-user:5}") int maximoPorUsuario,
            @Value("${app.login.max-attempts-per-ip:30}") int maximoPorIp,
            @Value("${app.login.window-minutes:15}") long ventanaMinutos,
            @Value("${app.login.lock-minutes:5}") long bloqueoMinutos,
            @Value("${app.login.max-lock-minutes:60}") long bloqueoMaximoMinutos) {
        this(maximoPorUsuario, maximoPorIp,
                Duration.ofMinutes(ventanaMinutos),
                Duration.ofMinutes(bloqueoMinutos),
                Duration.ofMinutes(bloqueoMaximoMinutos),
                Clock.systemUTC());
    }

    /** Para los tests: el reloj se mueve a mano y no hay que esperar de verdad. */
    LoginAttemptService(int maximoPorUsuario, int maximoPorIp, Duration ventana,
                        Duration bloqueoInicial, Duration bloqueoMaximo, Clock reloj) {
        this.maximoPorUsuario = maximoPorUsuario;
        this.maximoPorIp = maximoPorIp;
        this.ventana = ventana;
        this.bloqueoInicial = bloqueoInicial;
        this.bloqueoMaximo = bloqueoMaximo;
        this.reloj = reloj;
    }

    /**
     * Se llama <b>antes</b> de comprobar la contrasena, no despues. Asi un
     * bloqueado no llega a gastar un BCrypt de coste 12, que es justo lo que
     * busca quien manda peticiones a mansalva.
     *
     * @throws TooManyLoginAttemptsException si esa cuenta o esa IP estan bloqueadas
     */
    public void comprobar(String username, String ip) {
        restanteDe(claveDeUsuario(username)).ifPresent(restante -> {
            throw new TooManyLoginAttemptsException(
                    "Demasiados intentos fallidos con esta cuenta. " + reintentaEn(restante), restante);
        });
        restanteDe(claveDeIp(ip)).ifPresent(restante -> {
            throw new TooManyLoginAttemptsException(
                    "Demasiados intentos fallidos desde esta conexión. " + reintentaEn(restante), restante);
        });
    }

    /** Un intento que no ha colado. Suma a las dos cuentas. */
    public void anotarFallo(String username, String ip) {
        limpiarSiHaceFalta();
        anotarFallo(claveDeUsuario(username), maximoPorUsuario);
        anotarFallo(claveDeIp(ip), maximoPorIp);
    }

    /** Un login correcto. Borra las dos cuentas, incluido el historial de bloqueos. */
    public void anotarAcierto(String username, String ip) {
        intentos.remove(claveDeUsuario(username));
        intentos.remove(claveDeIp(ip));
    }

    /**
     * Olvida todo lo anotado. Existe para los tests, que si no se dejarian la
     * IP local bloqueada para las clases que corren despues.
     */
    public void olvidarTodo() {
        intentos.clear();
    }

    // ----------------------------------------------------------------

    private Optional<Duration> restanteDe(String clave) {
        Intentos actual = intentos.get(clave);
        if (actual == null || actual.bloqueadoHasta == null) {
            return Optional.empty();
        }
        Duration restante = Duration.between(reloj.instant(), actual.bloqueadoHasta);
        return restante.isNegative() || restante.isZero()
                ? Optional.empty()
                : Optional.of(restante);
    }

    private void anotarFallo(String clave, int maximo) {
        Instant ahora = reloj.instant();
        intentos.compute(clave, (k, actual) -> {
            Intentos estado = actual == null ? new Intentos() : actual;

            // Fuera de la ventana el contador vuelve a cero, pero el numero de
            // bloqueos no: si no, bastaria con esperar a que caducara la ventana
            // para que la espera volviera a ser la corta una y otra vez.
            if (estado.primerFallo == null || Duration.between(estado.primerFallo, ahora).compareTo(ventana) > 0) {
                estado.primerFallo = ahora;
                estado.fallos = 0;
            }
            estado.fallos++;

            if (estado.fallos >= maximo) {
                estado.bloqueadoHasta = ahora.plus(esperaTras(estado.bloqueos));
                estado.bloqueos++;
                estado.fallos = 0;
                estado.primerFallo = null;
                log.warn("Login bloqueado hasta {} tras {} intentos fallidos", estado.bloqueadoHasta, maximo);
            }
            return estado;
        });
    }

    /** Doblando desde el bloqueo inicial, sin pasar del tope. */
    private Duration esperaTras(int bloqueosPrevios) {
        Duration espera = bloqueoInicial;
        for (int i = 0; i < bloqueosPrevios && espera.compareTo(bloqueoMaximo) < 0; i++) {
            espera = espera.multipliedBy(2);
        }
        return espera.compareTo(bloqueoMaximo) > 0 ? bloqueoMaximo : espera;
    }

    private String reintentaEn(Duration restante) {
        long segundos = Math.max(1, restante.toSeconds());
        if (segundos < 60) {
            return "Vuelve a intentarlo en " + segundos + (segundos == 1 ? " segundo." : " segundos.");
        }
        long minutos = (segundos + 59) / 60;
        return "Vuelve a intentarlo en " + minutos + (minutos == 1 ? " minuto." : " minutos.");
    }

    private void limpiarSiHaceFalta() {
        if (intentos.size() < TAMANO_PARA_LIMPIAR) {
            return;
        }
        Instant limite = reloj.instant().minus(ventana).minus(bloqueoMaximo);
        intentos.values().removeIf(estado -> estado.caducadoAntesDe(limite));
    }

    private String claveDeUsuario(String username) {
        String normalizado = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return "u:" + resumen(normalizado);
    }

    private String claveDeIp(String ip) {
        return "ip:" + (ip == null ? "desconocida" : ip);
    }

    private static String resumen(String valor) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM; si falta, algo mucho peor pasa.
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static final class Intentos {
        private int fallos;
        private Instant primerFallo;
        private Instant bloqueadoHasta;
        private int bloqueos;

        boolean caducadoAntesDe(Instant limite) {
            Instant ultimaSenal = bloqueadoHasta != null ? bloqueadoHasta : primerFallo;
            return ultimaSenal == null || ultimaSenal.isBefore(limite);
        }
    }
}
