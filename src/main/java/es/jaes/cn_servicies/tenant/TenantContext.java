package es.jaes.cn_servicies.tenant;

import java.util.Optional;
import java.util.UUID;

/**
 * Club al que pertenece la peticion en curso.
 *
 * <p>El valor sale siempre del claim {@code club_id} del JWT, puesto por
 * {@link TenantFilter}. <b>Nunca</b> de un parametro, cabecera o cuerpo de la
 * peticion: el cliente podria enviar otro.
 *
 * <p><b>Puede estar vacio, y eso es correcto.</b> Los endpoints publicos —login,
 * alta de usuario, blog— no tienen club. Vacio significa "sin club", no "el club
 * por defecto": rellenarlo con uno cualquiera convertiria cada endpoint publico
 * en una via de escape del aislamiento.
 *
 * <p>Se apoya en un {@link ThreadLocal}, asi que <b>hay que limpiarlo al terminar
 * la peticion</b>. Tomcat reutiliza los hilos de su pool: sin limpieza, la
 * siguiente peticion que caiga en el mismo hilo hereda el club de la anterior,
 * de forma intermitente y practicamente imposible de reproducir. De eso se
 * encarga el {@code finally} de {@link TenantFilter}.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_CLUB = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID clubId) {
        CURRENT_CLUB.set(clubId);
    }

    /** Club de la peticion, vacio si es anonima. */
    public static Optional<UUID> get() {
        return Optional.ofNullable(CURRENT_CLUB.get());
    }

    /**
     * Club de la peticion, para el codigo que no puede funcionar sin uno.
     *
     * @throws IllegalStateException si no hay club, en vez de recurrir a
     *         ninguno por defecto
     */
    public static UUID require() {
        UUID clubId = CURRENT_CLUB.get();
        if (clubId == null) {
            throw new IllegalStateException(
                    "La peticion no tiene club asociado y esta operacion lo necesita");
        }
        return clubId;
    }

    public static boolean isSet() {
        return CURRENT_CLUB.get() != null;
    }

    /** {@code remove()}, no {@code set(null)}: deja la entrada fuera del mapa del hilo. */
    public static void clear() {
        CURRENT_CLUB.remove();
    }
}
