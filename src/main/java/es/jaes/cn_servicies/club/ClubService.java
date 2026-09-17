package es.jaes.cn_servicies.club;

import es.jaes.cn_servicies.user.RoleName;
import es.jaes.cn_servicies.user.UserRequest;
import es.jaes.cn_servicies.user.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.List;
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
     * Club dado de alta, por su slug, para lo que llega sin token: el blog
     * publico, el login y el alta publica. Cada frontend lleva configurado el
     * slug de su club y lo manda en la peticion.
     *
     * <p><b>No es una forma de fijar el club de la peticion.</b> Solo sirve para
     * acotar esa consulta o elegir la cuenta; el {@code TenantContext} sigue
     * saliendo del JWT y de nada mas.
     *
     * <p>Un club de baja responde igual que uno que no existe: su web deja de
     * servirse.
     */
    @Transactional(readOnly = true)
    public Club getActiveBySlug(String slug) {
        return clubRepository.findBySlug(slug)
                .filter(Club::isActive)
                .orElseThrow(() -> new EntityNotFoundException("Club no encontrado"));
    }

    public Club getById(UUID id) {
        return clubRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Club no encontrado"));
    }

    /**
     * Todos los clubes dados de alta, para los procesos que trabajan sobre el
     * sistema entero en vez de sobre una peticion — hoy el job que genera las
     * sesiones.
     *
     * <p><b>Es la unica consulta del proyecto que cruza clubes a proposito</b>,
     * y puede hacerlo porque {@code clubs} es la tabla raiz del tenant: no lleva
     * {@code club_id} ni policy, ya que es la lista de tenants y no datos de
     * uno. Quien la use tiene que fijar el {@code TenantContext} de cada club
     * antes de tocar sus datos.
     *
     * <p>Deja fuera los inactivos: dar de baja un club es {@code active = false},
     * y a partir de ahi no se le genera nada.
     */
    @Transactional(readOnly = true)
    public List<Club> findAllActive() {
        return clubRepository.findByActiveTrue();
    }
}
