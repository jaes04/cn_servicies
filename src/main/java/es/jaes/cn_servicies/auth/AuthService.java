package es.jaes.cn_servicies.auth;

import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.club.ClubService;
import es.jaes.cn_servicies.tenant.TenantContext;
import es.jaes.cn_servicies.user.UserRequest;
import es.jaes.cn_servicies.user.UserService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final ClubService clubService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Contra lo que se compara la contrasena cuando el usuario no existe. Ver
     * {@link #autenticar}.
     */
    private String hashFicticio;

    @PostConstruct
    void prepararHashFicticio() {
        hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * @param ip de donde llega el intento, para el limite por conexion. La
     *           averigua el controlador: el servicio no conoce la peticion HTTP.
     */
    public LoginResponse login(LoginRequest request, String ip) {
        // La cuenta del limite lleva el club: el mismo username es otra persona
        // en otro club, y los fallos contra el 'admin' de uno no pueden dejar
        // fuera al del otro.
        String cuenta = request.getClubSlug() + "/" + request.getUsername();

        // Antes de autenticar, no despues: un bloqueado no llega a gastar un
        // BCrypt, que es lo caro y lo que busca quien manda peticiones a mansalva.
        loginAttemptService.comprobar(cuenta, ip);

        Club club = clubService.getActiveBySlug(request.getClubSlug());

        AuthenticatedUser user;
        try {
            user = autenticar(club.getId(), request.getUsername(), request.getPassword());
        } catch (DisabledException e) {
            // Una cuenta bloqueada por el club no es un intento de adivinar la
            // contrasena: no cuenta para el limite, o el bloqueado recibiria un
            // "espera 5 minutos" en lugar de enterarse de lo que pasa.
            throw e;
        } catch (BadCredentialsException e) {
            loginAttemptService.anotarFallo(cuenta, ip);
            throw e;
        }

        loginAttemptService.anotarAcierto(cuenta, ip);

        return tokensPara(user);
    }

    public LoginResponse refresh(RefreshRequest request) {
        String token = request.getRefreshToken();

        if (!jwtTokenProvider.isValid(token, TokenType.REFRESH)) {
            throw new InvalidTokenException(porQueNoValeParaRefrescar(token));
        }

        // El club sale del token firmado, igual que en cada peticion: con el
        // username solo, el refresco de un 'admin' podria emitir los tokens del
        // 'admin' de otro club.
        AuthenticatedUser user = userDetailsService.loadUserByClubAndUsername(
                jwtTokenProvider.extractClubId(token),
                jwtTokenProvider.extractUsername(token));

        // Bloquear una cuenta tiene que cortarle tambien la renovacion. Si no,
        // el bloqueado se emite tokens nuevos con el de refresco que ya tenia y
        // el bloqueo no caduca nunca.
        if (!user.isEnabled()) {
            throw new DisabledException("Esta cuenta está bloqueada");
        }

        return tokensPara(user);
    }

    /**
     * Alta publica, anonima. El club es el del slug que manda el frontend; ya
     * no cae en el club por defecto, que con dos clubes metia a todo el mundo
     * en el mismo.
     */
    public LoginResponse signup(SignupRequest request) {
        Club club = clubService.getActiveBySlug(request.getClubSlug());

        UserRequest userRequest = new UserRequest();
        userRequest.setUsername(request.getUsername());
        userRequest.setEmail(request.getEmail());
        userRequest.setPassword(request.getPassword());
        userService.create(userRequest, club);

        return tokensPara(userDetailsService.loadUserByClubAndUsername(club.getId(), request.getUsername()));
    }

    /**
     * Alta con rol. La hace un administrador autenticado, asi que el club es el
     * de su token; no lleva slug.
     */
    public LoginResponse signupWithRole(SignupWithRoleRequest request) {
        Club club = clubService.getById(TenantContext.require());

        UserRequest userRequest = new UserRequest();
        userRequest.setUsername(request.getUsername());
        userRequest.setEmail(request.getEmail());
        userRequest.setPassword(request.getPassword());
        userRequest.setRoles(request.getRoles());
        userService.create(userRequest, club);

        return tokensPara(userDetailsService.loadUserByClubAndUsername(club.getId(), request.getUsername()));
    }

    /**
     * Comprueba usuario y contrasena dentro de un club, con las mismas reglas
     * que aplicaba el proveedor de Spring Security.
     *
     * <p>No pasa por el {@code AuthenticationManager} porque ese resuelve la
     * cuenta solo por username, y sin el club es ambigua.
     *
     * <ul>
     *   <li>Una cuenta bloqueada da {@link DisabledException} antes de mirar la
     *       contrasena, como hacia Spring: el mensaje ya admite que existe.
     *   <li>Usuario inexistente y contrasena mala dan la misma
     *       {@link BadCredentialsException}.
     *   <li><b>Si el usuario no existe se gasta igualmente un BCrypt</b> contra
     *       {@link #hashFicticio}. Sin eso, la respuesta a un username que no
     *       existe llega 250 ms antes, y cronometrando se sabe que cuentas hay.
     * </ul>
     */
    private AuthenticatedUser autenticar(UUID clubId, String username, String password) {
        AuthenticatedUser user;
        try {
            user = userDetailsService.loadUserByClubAndUsername(clubId, username);
        } catch (UsernameNotFoundException e) {
            passwordEncoder.matches(password, hashFicticio);
            throw new BadCredentialsException("Usuario o contraseña incorrectos");
        }

        if (!user.isEnabled()) {
            throw new DisabledException("Esta cuenta está bloqueada");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("Usuario o contraseña incorrectos");
        }
        return user;
    }

    private LoginResponse tokensPara(AuthenticatedUser user) {
        return new LoginResponse(jwtTokenProvider.generateAccessToken(user), jwtTokenProvider.generateRefreshToken(user));
    }

    /**
     * Por que ese token no sirve para refrescar.
     *
     * <p>Decir que el token enviado es el de acceso no filtra nada —quien
     * pregunta ya lo tiene en la mano— y ahorra la tarde de depuracion que se
     * lleva un "token invalido" cuando el token es perfectamente valido y solo
     * esta en la ruta equivocada.
     */
    private String porQueNoValeParaRefrescar(String token) {
        if (jwtTokenProvider.isValid(token, TokenType.ACCESS)) {
            return "Esta ruta solo acepta el refresh token, y has enviado el de acceso";
        }
        return "El refresh token no es válido o ha caducado. Vuelve a iniciar sesión";
    }
}
