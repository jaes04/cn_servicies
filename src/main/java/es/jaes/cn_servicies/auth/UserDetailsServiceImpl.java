package es.jaes.cn_servicies.auth;


import es.jaes.cn_servicies.user.User;
import es.jaes.cn_servicies.user.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * La cuenta de un club. <b>Es la unica forma de cargar un usuario para
     * autenticar</b>: el username es unico por club, asi que sin el club la
     * busqueda es ambigua en cuanto dos clubes tienen un 'admin'.
     *
     * <p>El club llega de fuentes distintas segun el camino: en el login y el
     * alta publica, del slug que manda el frontend; en cada peticion y al
     * refrescar, del claim {@code club_id} del token firmado.
     *
     * <p>Transaccional para poder leer los roles, que son perezosos. Se ejecuta
     * antes de que exista club en el {@code TenantContext}, asi que las policies
     * van en modo {@code public}: lo que acota al club es la propia consulta.
     */
    @Transactional(readOnly = true)
    public AuthenticatedUser loadUserByClubAndUsername(UUID clubId, String username) {
        User user = userRepository.findByClubIdAndUsername(clubId, username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toSet());

        return new AuthenticatedUser(
                user.getUsername(),
                user.getPasswordHash(),
                !user.isBlocked(),
                user.getClub().getId(),
                authorities);
    }

    /**
     * No se usa, y falla cerrado a proposito. Existe porque lo exige
     * {@link UserDetailsService}, que es lo que Spring Security necesita
     * registrado para no montar un usuario en memoria por su cuenta.
     *
     * <p>Sin club no hay forma de saber de quien es el username. Resolverlo
     * aqui con el primero que aparezca autenticaria a la cuenta de otro club.
     * Usa {@link #loadUserByClubAndUsername}.
     */
    @Override
    public AuthenticatedUser loadUserByUsername(String username) {
        throw new UsernameNotFoundException(
                "No se puede cargar un usuario sin su club: usa loadUserByClubAndUsername");
    }
}
