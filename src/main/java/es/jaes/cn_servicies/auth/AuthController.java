package es.jaes.cn_servicies.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/signup")
    public ResponseEntity<LoginResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    /**
     * Alta de usuario indicando roles. Restringido a ROLE_ADMIN en SecurityConfig:
     * el cuerpo de la peticion decide los roles, asi que abierto equivale a regalar
     * ROLE_ADMIN a cualquiera.
     */
    @PostMapping("/signup/with-role")
    public ResponseEntity<LoginResponse> signupWithRole(@Valid @RequestBody SignupWithRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signupWithRole(request));
    }

}