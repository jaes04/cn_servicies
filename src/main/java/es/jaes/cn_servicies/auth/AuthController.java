package es.jaes.cn_servicies.auth;

import jakarta.servlet.http.HttpServletRequest;
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

    /**
     * La IP sale de {@code getRemoteAddr()}, que es la del que abre la conexion.
     *
     * <p>Detras de un proxy —Cloudflare Tunnel, en produccion— esa es la del
     * proxy y todo el mundo comparte limite. Se arregla con
     * {@code server.forward-headers-strategy=framework}, que hace que Spring lea
     * {@code X-Forwarded-For}. <b>No se activa por defecto a proposito</b>: sin
     * un proxy delante que la reescriba, esa cabecera la pone quien quiere y
     * cualquiera se inventaria una IP por intento.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest http) {
        return ResponseEntity.ok(authService.login(request, http.getRemoteAddr()));
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