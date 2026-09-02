package es.jaes.cn_servicies.auth;


import es.jaes.cn_servicies.user.User;
import es.jaes.cn_servicies.user.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Transaccional para poder leer el club, que es una asociacion perezosa.
     *
     * <p>Sigue resolviendo por username a secas, y eso solo es correcto
     * mientras haya un unico club: el username es unico por club, asi que con
     * dos esta consulta se vuelve ambigua. Determinar el club antes de
     * autenticar depende de como se sirva cada uno —subdominio, slug en la
     * peticion o selector en el formulario—, que es decision abierta.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
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
}