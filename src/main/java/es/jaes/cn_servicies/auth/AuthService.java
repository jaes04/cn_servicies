package es.jaes.cn_servicies.auth;

import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.club.ClubService;
import es.jaes.cn_servicies.tenant.TenantContext;
import es.jaes.cn_servicies.user.UserRequest;
import es.jaes.cn_servicies.user.UserResponse;
import es.jaes.cn_servicies.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final ClubService clubService;
    private final LoginAttemptService loginAttemptService;

    /**
     * @param ip de donde llega el intento, para el limite por conexion. La
     *           averigua el controlador: el servicio no conoce la peticion HTTP.
     */
    public LoginResponse login(LoginRequest request, String ip) {
        // Antes de autenticar, no despues: un bloqueado no llega a gastar un
        // BCrypt, que es lo caro y lo que busca quien manda peticiones a mansalva.
        loginAttemptService.comprobar(request.getUsername(), ip);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );
        } catch (DisabledException e) {
            // Una cuenta bloqueada por el club no es un intento de adivinar la
            // contrasena: no cuenta para el limite, o el bloqueado recibiria un
            // "espera 5 minutos" en lugar de enterarse de lo que pasa.
            throw e;
        } catch (AuthenticationException e) {
            loginAttemptService.anotarFallo(request.getUsername(), ip);
            throw e;
        }

        loginAttemptService.anotarAcierto(request.getUsername(), ip);

        UserDetails user = userDetailsService.loadUserByUsername(request.getUsername());
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        return new LoginResponse(accessToken, refreshToken);
    }

    public LoginResponse refresh(RefreshRequest request) {
        String token = request.getRefreshToken();

        if (!jwtTokenProvider.isValid(token, TokenType.REFRESH)) {
            throw new InvalidTokenException(porQueNoValeParaRefrescar(token));
        }

        String username = jwtTokenProvider.extractUsername(token);
        UserDetails user = userDetailsService.loadUserByUsername(username);

        // Bloquear una cuenta tiene que cortarle tambien la renovacion. Si no,
        // el bloqueado se emite tokens nuevos con el de refresco que ya tenia y
        // el bloqueo no caduca nunca.
        if (!user.isEnabled()) {
            throw new DisabledException("Esta cuenta está bloqueada");
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user);

        return new LoginResponse(newAccessToken, newRefreshToken);
    }

    public LoginResponse signup(SignupRequest request) {
        UserRequest userRequest = new UserRequest();
        userRequest.setUsername(request.getUsername());
        userRequest.setEmail(request.getEmail());
        userRequest.setPassword(request.getPassword());
        userService.create(userRequest, clubDeLaPeticion());

        UserDetails user = userDetailsService.loadUserByUsername(request.getUsername());
        return new LoginResponse(jwtTokenProvider.generateAccessToken(user), jwtTokenProvider.generateRefreshToken(user));
    }

    public LoginResponse signupWithRole(SignupWithRoleRequest request) {
        UserRequest userRequest = new UserRequest();
        userRequest.setUsername(request.getUsername());
        userRequest.setEmail(request.getEmail());
        userRequest.setPassword(request.getPassword());
        userRequest.setRoles(request.getRoles());
        userService.create(userRequest, clubDeLaPeticion());

        UserDetails user = userDetailsService.loadUserByUsername(request.getUsername());
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

    /**
     * Club en el que se da de alta la cuenta.
     *
     * <p>El alta con rol la hace un administrador y trae club en contexto. El
     * alta publica es anonima y no lo trae: en que club se registra alguien que
     * llega de fuera depende de como se sirva cada club, que es la misma
     * decision abierta que la del login. Mientras solo haya uno, el club por
     * defecto es la respuesta correcta; con dos, hay que resolverlo antes.
     */
    private Club clubDeLaPeticion() {
        return TenantContext.get()
                .map(clubService::getById)
                .orElseGet(clubService::getDefaultClub);
    }
}
