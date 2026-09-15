package es.jaes.cn_servicies.access;

import es.jaes.cn_servicies.athlete.AthleteService;
import es.jaes.cn_servicies.athlete_document.AthleteDocumentService;
import es.jaes.cn_servicies.athlete_link.UserAthleteService;
import es.jaes.cn_servicies.competition_result.CompetitionResultService;
import es.jaes.cn_servicies.training_group.AthleteGroupService;
import es.jaes.cn_servicies.training_group.TrainingGroupService;
import es.jaes.cn_servicies.training_session.TrainingSessionService;
import es.jaes.cn_servicies.user.RoleName;
import es.jaes.cn_servicies.user.User;
import es.jaes.cn_servicies.user.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Autorizacion a nivel de objeto (tarea S.3.3).
 *
 * <p>El {@code club_id} no protege del IDOR interno: un usuario del club A
 * pidiendo por id algo del club A pasa el filtro de tenancy y las policies de
 * RLS sin despeinarse, porque el recurso <b>es</b> de su club. Lo que decide si
 * puede verlo es su relacion con ese recurso, y eso no lo sabe ni Hibernate ni
 * Postgres. Aqui.
 *
 * <h2>Quien llega a que</h2>
 * <ul>
 *   <li><b>Administrador:</b> a todo su club.</li>
 *   <li><b>Entrenador:</b> a los grupos que lleva —como principal o como
 *       ayudante— y a sus sesiones; y a los atletas que <b>hoy</b> estan en
 *       alguno de esos grupos. Un nadador que dejo el grupo la semana pasada deja
 *       de ser visible en su ficha, aunque siga en los rosters de las sesiones
 *       pasadas, porque a esas se llega por el grupo y no por el atleta.</li>
 *   <li><b>Cualquier cuenta:</b> a los atletas con los que tiene
 *       {@code UserAthlete}. Tambien un entrenador cuyo hijo nada en el club.</li>
 * </ul>
 *
 * <p><b>Paquete propio y no {@code config/}</b>, que {@code CLAUDE.md} avisa de
 * no convertir en cajon. Depende de los servicios de los otros modulos —nunca de
 * sus repositorios— y responde una sola pregunta: "¿es tuyo?".
 *
 * <p><b>Se llama desde los controladores, nunca desde los servicios.</b> Es lo
 * que evita los ciclos: este guardian depende de media aplicacion, y en cuanto
 * un servicio preguntara aqui Spring no arrancaria. La contrapartida es que
 * quien llegue al servicio por otra via se lo salta; hoy no hay ninguna, y lo
 * que lo sostiene son los tests de endpoint.
 *
 * <p><b>Deniega con 404, no con 403</b>, siguiendo {@code docs/convenciones.md}:
 * un recurso que existe pero no es tuyo no puede distinguirse de uno que no
 * existe, o el propio codigo de error confirma que existe. Por eso los metodos
 * se llaman {@code require*} y no devuelven {@code boolean}.
 */
@Component
@RequiredArgsConstructor
public class AccessGuard {

    private final AthleteService athleteService;
    private final AthleteDocumentService documentService;
    private final AthleteGroupService membershipService;
    private final CompetitionResultService resultService;
    private final TrainingGroupService groupService;
    private final TrainingSessionService sessionService;
    private final UserAthleteService userAthleteService;
    private final UserService userService;

    // ----------------------------------------------------------------
    //  Grupos y sesiones
    // ----------------------------------------------------------------

    /**
     * Exige que quien pide pueda entrar en ese grupo.
     *
     * @throws EntityNotFoundException si no existe, es de otro club, esta borrado
     *                                 o no lo lleva
     */
    public void requireGroupAccess(UUID groupId) {
        // Primero la carga, que pasa por RLS y por el borrado logico: un grupo de
        // otro club o borrado se cae aqui con el mismo mensaje.
        groupService.findOrThrow(groupId);
        if (!groupAccessible(groupId)) {
            throw new EntityNotFoundException("Grupo no encontrado");
        }
    }

    /**
     * Lo mismo para una sesion, que hereda el permiso de su grupo.
     *
     * <p>Es la puerta del roster y de pasar lista: la ruta que usa el movil, con
     * el id de la sesion suelto.
     */
    public void requireSessionAccess(UUID sessionId) {
        UUID groupId = sessionService.findOrThrow(sessionId).getTrainingGroup().getId();
        if (!groupAccessible(groupId)) {
            throw new EntityNotFoundException("Sesión no encontrada");
        }
    }

    // ----------------------------------------------------------------
    //  Atletas y lo que cuelga de ellos
    // ----------------------------------------------------------------

    /**
     * Exige que quien pide pueda llegar a ese atleta.
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
     * <p>{@code athlete_documents} es tabla hija, no lleva {@code club_id} ni
     * policy, y su id viaja suelto en la URL de descarga. Sin esta comprobacion,
     * un id basta para abrir el archivo — y el tipo {@code MEDICAL} es lo mas
     * sensible que guarda el sistema.
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

    /** Lo mismo para un resultado de competicion. Mismo criterio con el mensaje. */
    public void requireResultAccess(UUID resultId) {
        UUID athleteId = resultService.athleteIdOf(resultId);
        if (!athleteAccessible(athleteId)) {
            throw new EntityNotFoundException("Resultado no encontrado");
        }
    }

    // ----------------------------------------------------------------
    //  Cuentas
    // ----------------------------------------------------------------

    /**
     * Exige que la cuenta sobre la que se escribe sea la propia, o que quien
     * escriba sea administrador del club.
     */
    public void requireUserAccess(UUID userId) {
        if (isAdmin()) {
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

    // ----------------------------------------------------------------
    //  Listados
    // ----------------------------------------------------------------

    /**
     * Si quien pide ve el club entero. Cuando no, los listados se acotan con
     * {@link #visibleGroupIds()} o {@link #visibleAthleteIds()}.
     */
    public boolean seesWholeClub() {
        return isAdmin();
    }

    /** Los grupos que lleva quien pide, como principal o como ayudante. Vacio si no es entrenador. */
    public Set<UUID> visibleGroupIds() {
        if (!isCoach()) {
            return Set.of();
        }
        return groupService.coachedGroupIds(currentUser().getId());
    }

    /**
     * Los atletas a los que llega quien pide: los que hoy estan en sus grupos,
     * si es entrenador, mas los que tenga vinculados.
     */
    public Set<UUID> visibleAthleteIds() {
        Set<UUID> ids = new HashSet<>(userAthleteService.linkedAthleteIds(currentUsername()));
        if (isCoach()) {
            ids.addAll(membershipService.athleteIdsInGroupsOn(visibleGroupIds(), LocalDate.now()));
        }
        return ids;
    }

    // ----------------------------------------------------------------

    private boolean groupAccessible(UUID groupId) {
        return isAdmin() || visibleGroupIds().contains(groupId);
    }

    private boolean athleteAccessible(UUID athleteId) {
        try {
            // Primero la carga por id, que es la que pasa por RLS: un atleta de
            // otro club se cae aqui y no se llega a mirar ningun vinculo.
            athleteService.findOrThrow(athleteId);
        } catch (EntityNotFoundException e) {
            return false;
        }
        return isAdmin() || visibleAthleteIds().contains(athleteId);
    }

    private boolean isAdmin() {
        return hasRole(RoleName.ROLE_ADMIN);
    }

    private boolean isCoach() {
        return hasRole(RoleName.ROLE_TECHNICAL_STAFF);
    }

    private boolean hasRole(RoleName role) {
        Authentication auth = authentication();
        return auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role.name()::equals);
    }

    private User currentUser() {
        return userService.findEntityByUsername(currentUsername());
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
