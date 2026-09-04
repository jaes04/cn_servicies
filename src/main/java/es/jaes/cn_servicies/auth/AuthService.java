package es.jaes.cn_servicies.auth;

import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.club.ClubService;
import es.jaes.cn_servicies.tenant.TenantContext;
import es.jaes.cn_servicies.user.UserRequest;
import es.jaes.cn_servicies.user.UserResponse;
import es.jaes.cn_servicies.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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

    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        UserDetails user = userDetailsService.loadUserByUsername(request.getUsername());
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        return new LoginResponse(accessToken, refreshToken);
    }

    public LoginResponse refresh(RefreshRequest request) {
        String token = request.getRefreshToken();

        if (!jwtTokenProvider.isValid(token)) {
            throw new IllegalArgumentException("Refresh token inválido o expirado");
        }

        String username = jwtTokenProvider.extractUsername(token);
        UserDetails user = userDetailsService.loadUserByUsername(username);

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
