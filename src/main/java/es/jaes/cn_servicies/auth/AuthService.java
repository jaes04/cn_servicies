package es.jaes.cn_servicies.auth;

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
        userService.create(userRequest);

        UserDetails user = userDetailsService.loadUserByUsername(request.getUsername());
        return new LoginResponse(jwtTokenProvider.generateAccessToken(user), jwtTokenProvider.generateRefreshToken(user));
    }

    public LoginResponse signupWithRole(SignupWithRoleRequest request) {
        UserRequest userRequest = new UserRequest();
        userRequest.setUsername(request.getUsername());
        userRequest.setEmail(request.getEmail());
        userRequest.setPassword(request.getPassword());
        userRequest.setRoles(request.getRoles());
        userService.create(userRequest);

        UserDetails user = userDetailsService.loadUserByUsername(request.getUsername());
        return new LoginResponse(jwtTokenProvider.generateAccessToken(user), jwtTokenProvider.generateRefreshToken(user));
    }
}