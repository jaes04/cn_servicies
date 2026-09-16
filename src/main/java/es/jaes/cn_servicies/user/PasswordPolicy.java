package es.jaes.cn_servicies.user;

import es.jaes.cn_servicies.club.Club;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * La unica regla de que contrasenas se aceptan.
 *
 * <p>Vive aparte de los DTO y se aplica en {@link UserService}, que es por donde
 * pasan <b>todas</b> las contrasenas del sistema: el alta publica, el alta desde
 * la administracion, el club recien creado con su administrador, el arranque con
 * {@code ADMIN_PASSWORD}, el cambio propio y el que fija el administrador. Como
 * anotacion en los DTO se habria quedado fuera de los tres ultimos.
 *
 * <p><b>Longitud y contexto, sin composicion forzada.</b> No se exige mayuscula,
 * numero ni simbolo: esas reglas producen {@code Password1!} —que es corta, esta
 * en todas las listas y encima hay que apuntarla— y no producen contrasenas
 * mejores. Doce caracteres y que no sea una de las conocidas rinde mucho mas,
 * que es lo que recomienda el NIST desde 2017.
 *
 * <p>Lo que se comprueba, en este orden y con un mensaje distinto cada uno:
 * longitud minima, longitud maxima, lista de conocidas, que no lleve dentro el
 * usuario o el correo, que no lleve el nombre del club, y que no sea una
 * repeticion.
 */
@Service
public class PasswordPolicy {

    private static final Logger log = LoggerFactory.getLogger(PasswordPolicy.class);

    private static final String LISTA_INCLUIDA = "passwords-comunes.txt";

    /**
     * BCrypt solo mira los primeros 72 <b>bytes</b> y tira el resto sin avisar.
     * Sin este tope, dos contrasenas larguisimas que empiezan igual serian la
     * misma contrasena y nadie lo sabria. Se mide en bytes porque una letra con
     * acento ocupa dos.
     */
    private static final int MAXIMO_BYTES = 72;

    /** Por debajo de esto, un trozo comun —"ana", "cn"— saltaria en cualquier contrasena. */
    private static final int MINIMO_PARA_BUSCAR_DENTRO = 4;

    private final int minimo;
    private final String archivoExterno;
    private final Set<String> conocidas = new HashSet<>();

    public PasswordPolicy(
            @Value("${app.security.password.min-length:12}") int minimo,
            @Value("${app.security.password.blocklist-file:}") String archivoExterno) {
        this.minimo = minimo;
        this.archivoExterno = archivoExterno;
    }

    @PostConstruct
    void cargarListas() {
        cargar(LISTA_INCLUIDA);
        if (archivoExterno != null && !archivoExterno.isBlank()) {
            cargarArchivo(Path.of(archivoExterno));
        }
        log.info("Politica de contrasenas: minimo {} caracteres, {} contrasenas en la lista.",
                minimo, conocidas.size());
    }

    /**
     * @param club puede ser {@code null} si todavia no se conoce
     * @throws IllegalArgumentException con el motivo, si no vale
     */
    public void comprobar(String password, String username, String email, Club club) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("La contraseña no puede estar vacía");
        }
        if (password.length() < minimo) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos " + minimo + " caracteres");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES) {
            throw new IllegalArgumentException(
                    "La contraseña no puede pasar de " + MAXIMO_BYTES + " caracteres");
        }

        String limpia = normalizar(password);

        if (conocidas.contains(limpia)) {
            throw new IllegalArgumentException(
                    "Esa contraseña es de las más usadas y está en listas públicas. Elige otra");
        }
        if (contiene(limpia, username) || contiene(limpia, parteLocal(email))) {
            throw new IllegalArgumentException(
                    "La contraseña no puede llevar dentro tu usuario ni tu correo");
        }
        if (club != null && (contiene(limpia, club.getName()) || contiene(limpia, club.getSlug()))) {
            throw new IllegalArgumentException(
                    "La contraseña no puede llevar dentro el nombre del club");
        }
        if (esRepeticion(limpia)) {
            throw new IllegalArgumentException(
                    "La contraseña no puede ser la misma cosa repetida. Alárgala con algo distinto");
        }
    }

    // ----------------------------------------------------------------

    /**
     * Minusculas, sin acentos y sin nada que no sea letra o numero.
     *
     * <p>Asi {@code Contraseña-123} y {@code contrasena123} son la misma a la
     * hora de compararlas: quien esquiva la lista poniendo un guion no ha
     * mejorado nada.
     */
    private static String normalizar(String valor) {
        String sinAcentos = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static boolean contiene(String passwordNormalizada, String valor) {
        if (valor == null || valor.isBlank()) {
            return false;
        }
        String aguja = normalizar(valor);
        return aguja.length() >= MINIMO_PARA_BUSCAR_DENTRO && passwordNormalizada.contains(aguja);
    }

    private static String parteLocal(String email) {
        if (email == null) {
            return null;
        }
        int arroba = email.indexOf('@');
        return arroba > 0 ? email.substring(0, arroba) : email;
    }

    /**
     * Si toda la contrasena es un trozo corto repetido: {@code aaaaaaaaaaaa},
     * {@code abcabcabcabc}. Llegan al minimo de longitud sin aportar nada.
     */
    private static boolean esRepeticion(String limpia) {
        if (limpia.isEmpty()) {
            return false;
        }
        for (int trozo = 1; trozo <= 4 && trozo < limpia.length(); trozo++) {
            if (limpia.length() % trozo != 0) {
                continue;
            }
            String patron = limpia.substring(0, trozo);
            if (patron.repeat(limpia.length() / trozo).equals(limpia)) {
                return true;
            }
        }
        return false;
    }

    private void cargar(String recurso) {
        ClassPathResource archivo = new ClassPathResource(recurso);
        try (BufferedReader lector = new BufferedReader(
                new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8))) {
            lector.lines().forEach(this::anadir);
        } catch (IOException e) {
            // Sin lista, la politica seguiria aceptando "administrador123". Es
            // preferible no arrancar que arrancar con media comprobacion.
            throw new IllegalStateException("No se puede leer la lista de contraseñas " + recurso, e);
        }
    }

    private void cargarArchivo(Path ruta) {
        try {
            Files.readAllLines(ruta, StandardCharsets.UTF_8).forEach(this::anadir);
        } catch (IOException e) {
            throw new IllegalStateException("No se puede leer la lista de contraseñas " + ruta, e);
        }
    }

    private void anadir(String linea) {
        String limpia = linea.strip();
        if (limpia.isEmpty() || limpia.startsWith("#")) {
            return;
        }
        conocidas.add(normalizar(limpia));
    }
}
