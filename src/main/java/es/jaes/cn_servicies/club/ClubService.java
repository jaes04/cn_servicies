package es.jaes.cn_servicies.club;

import es.jaes.cn_servicies.user.RoleName;
import es.jaes.cn_servicies.user.UserRequest;
import es.jaes.cn_servicies.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Superficie publica del modulo club. Los demas modulos pasan por aqui, nunca
 * por {@link ClubRepository}.
 */
@Service
@RequiredArgsConstructor
public class ClubService {

    private final ClubRepository clubRepository;
    private final UserService userService;

    @Value("${app.default-club-slug:sierra-oeste}")
    private String defaultClubSlug;

    @Value("${app.default-club-name:Club de Natacion}")
    private String defaultClubName;

    /** El club por defecto, si existe. Sin exigir que exista. */
    public Optional<Club> findDefault() {
        return clubRepository.findBySlug(defaultClubSlug);
    }

    /** Alta del club por defecto con su administrador, para el primer arranque. */
    @Transactional
    public Club createDefault(String adminUsername, String adminEmail, String adminPassword) {
        return create(defaultClubName, defaultClubSlug, adminUsername, adminEmail, adminPassword);
    }

    /**
     * Da de alta un club junto con su administrador, en una sola transaccion.
     *
     * <p><b>Nunca existe un club sin administrador.</b> Si el alta del usuario
     * falla —el email ya esta en uso, falta el rol— la transaccion se deshace y
     * el club tampoco se crea. Un club sin nadie que pueda entrar seria un
     * registro muerto que solo se puede arreglar a mano en la base.
     *
     * <p>Ese administrador no tiene nada de especial: es un {@code ROLE_ADMIN}
     * de <b>este</b> club, sujeto al filtro y a las policies como cualquier
     * otro. Su proposito es poder crear los demas administradores del club, no
     * ver sus datos desde fuera. No hay ningun rol que atraviese clubes.
     */
    @Transactional
    public Club create(String nombre, String slug, String adminUsername,
                       String adminEmail, String adminPassword) {
        if (clubRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Ya existe un club con ese slug");
        }

        Club club = new Club();
        club.setName(nombre);
        club.setSlug(slug);
        club.setActive(true);
        Club creado = clubRepository.saveAndFlush(club);

        UserRequest admin = new UserRequest();
        admin.setUsername(adminUsername);
        admin.setEmail(adminEmail);
        admin.setPassword(adminPassword);
        admin.setRoles(Set.of(RoleName.ROLE_ADMIN));
        userService.create(admin, creado);

        return creado;
    }

    /**
     * Club al que se asigna todo lo que se crea mientras no exista contexto de
     * tenant.
     *
     * <p><b>Temporal.</b> Desaparece en la tarea 0.4: a partir de ahi el club
     * sale del {@code TenantContext}, que a su vez lo toma del claim del JWT.
     * Mientras tanto la aplicacion sigue siendo mono-club y esta es la unica
     * forma de satisfacer el {@code NOT NULL} de {@code club_id} sin inventar
     * un club por peticion.
     */
    public Club getDefaultClub() {
        return clubRepository.findBySlug(defaultClubSlug)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el club por defecto con slug '" + defaultClubSlug + "'"));
    }

    public Club getById(UUID id) {
        return clubRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Club no encontrado"));
    }
}
