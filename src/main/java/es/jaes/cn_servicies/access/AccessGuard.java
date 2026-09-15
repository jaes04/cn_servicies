package es.jaes.cn_servicies.access;

import es.jaes.cn_servicies.athlete.AthleteService;
import es.jaes.cn_servicies.athlete_document.AthleteDocumentService;
import es.jaes.cn_servicies.athlete_link.UserAthleteService;
import es.jaes.cn_servicies.user.RoleName;
import es.jaes.cn_servicies.user.User;
import es.jaes.cn_servicies.user.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Autorizacion a nivel de objeto (tarea S.3.3).
 *
 * <p>El {@code club_id} no protege del IDOR interno: un usuario del club A
 * pidiendo por id algo del club A pasa el filtro de tenancy y las policies de
 * RLS sin despeinarse, porque el recurso <b>es</b> de su club. Lo que decide si
 * puede verlo es su vinculo con el atleta, y eso no lo sabe ni Hibernate ni
 * Postgres. Aqui.
 *
 * <p><b>Paquete propio y no {@code config/}</b>, que {@code CLAUDE.md} avisa de
 * no convertir en cajon. Es un modulo mas: depende de los servicios de los otros
 * —nunca de sus repositorios— y responde una sola pregunta.
 *
 * <p><b>Se llama desde los controladores, nunca desde los servicios.</b> Es lo
 * que evita el ciclo {@code athlete -> access -> athlete_link -> athlete}, que
 * apareceria en cuanto un servicio preguntara aqui y Spring no arrancaria. La
 * contrapartida es que quien llame al servicio por otra via se salta el
 * guardian: hoy no hay ninguna, y lo que lo sostiene son los tests de endpoint.
 *
 * <p><b>Deniega con 404, no con 403</b>, siguiendo {@code docs/convenciones.md}:
 * un recurso que existe pero no es tuyo no puede distinguirse de uno que no
 * existe, o el propio codigo de error confirma que ese atleta esta fichado en el
 * club. Por eso los metodos no devuelven {@code boolean} y se llaman
 * {@code require*}: no hay respuesta "no" que devolver, hay una puerta que se
 * cierra.
 */
@Component
@RequiredArgsConstructor
public class AccessGuard {

    /**
     * Quien ve a cualquier atleta de su club por el rol que tiene.
     *
     * <p>{@code ROLE_TECHNICAL_STAFF} esta aqui de momento: acotarlo a los
     * grupos que entrena es el bloque siguiente, y meterlo en este mezclaria dos
     * cambios de contrato en el mismo commit.
     */
    private static final Set<String> ROLES_DE_CLUB =
            Set.of(RoleName.ROLE_ADMIN.name(), RoleName.ROLE_TECHNICAL_STAFF.name());

    private final AthleteService athleteService;
    private final AthleteDocumentService documentService;
    private final UserAthleteService userAthleteService;
    private final UserService userService;

    /**
     * Exige que quien hace la peticion pueda llegar a ese atleta.
     *
     * @throws EntityNotFoundException si el atleta no existe, es de otro club, o
     *                                 es de este pero no le corresponde
     */
    public void requireAthleteAccess(UUID athleteId) {
        if (!athleteAccessible(athleteId)) {
            throw new EntityNotFoundException("Atleta no encontrado");
        }
    }

    /**
     * Lo mismo para un documento, que hereda el permiso de su atleta.
     *
     * <p>Es el que mas importa de los tres: {@code athlete_documents} es tabla
     * hija, no lleva {@code club_id} ni policy, y su id viaja suelto en la URL
     * de descarga. Sin esta comprobacion, un id basta para abrir el archivo — y
     * el tipo {@code MEDICAL} es lo mas sensible que guarda el sistema.
     *
     * <p>El mensaje es el del documento tambien cuando quien falla es el atleta:
     * "atleta no encontrado" ante un id de documento valido diria que el
     * documento si existe.
     */
    public void requireDocumentAccess(UUID documentId) {
        UUID athleteId = documentService.athleteIdOf(documentId);
        if (!athleteAccessible(athleteId)) {
            throw new EntityNotFoundException("Documento no encontrado");
        }
    }

    /**
     * Exige que la cuenta sobre la que se escribe sea la propia, o que quien
     * escriba sea administrador del club.
     */
    public void requireUserAccess(UUID userId) {
        if (hasClubRole()) {
            // Carga por id para que RLS diga lo suyo: el administrador del club
            // A no escribe sobre una cuenta del club B.
            userService.findEntityById(userId);
            return;
        }

        User quienPide = userService.findEntityByUsername(currentUsername());
        if (!quienPide.getId().equals(userId)) {
            throw new EntityNotFoundException("Usuario no encontrado");
        }
    }

    private boolean athleteAccessible(UUID athleteId) {
        try {
            // Primero la carga por id, que es la que pasa por RLS: un atleta de
            // otro club se cae aqui y no se llega a mirar ningun vinculo.
            athleteService.findOrThrow(athleteId);
        } catch (EntityNotFoundException e) {
            return false;
        }

        if (hasClubRole()) {
            return true;
        }

        // Un tutor o un socio llega solo a los suyos. UserAthlete es el vinculo
        // de acceso; Guardian es otra cosa —quien otorga el consentimiento— y no
        // se sustituyen. Ver CLAUDE.md.
        return userAthleteService.isLinkedTo(currentUsername(), athleteId);
    }

    private boolean hasClubRole() {
        Authentication auth = authentication();
        return auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLES_DE_CLUB::contains);
    }

    private String currentUsername() {
        Authentication auth = authentication();
        if (auth == null) {
            // Los endpoints que preguntan aqui estan todos detras de
            // authenticated(), asi que esto no deberia ocurrir. Si ocurre, se
            // niega igual que cualquier otra cosa que no cuadra.
            throw new EntityNotFoundException("Recurso no encontrado");
        }
        return auth.getName();
    }

    private Authentication authentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() ? auth : null;
    }
}
