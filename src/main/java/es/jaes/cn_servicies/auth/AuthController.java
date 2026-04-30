package es.jaes.cn_servicies.auth;

import es.jaes.cn_servicies.user.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @PostMapping("/signup/with-role")
    public ResponseEntity<UserResponse> signupWithRole(@Valid @RequestBody SignupWithRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signupWithRole(request));
    }

    // TEMPORAL: genera hash BCrypt — eliminar tras las pruebas
    @GetMapping("/hash")
    public ResponseEntity<String> hash(@RequestParam String raw) {
        return ResponseEntity.ok(passwordEncoder.encode(raw));
    }
}