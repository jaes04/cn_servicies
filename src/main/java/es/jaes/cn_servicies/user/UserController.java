package es.jaes.cn_servicies.user;

import es.jaes.cn_servicies.access.AccessGuard;
import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.club.ClubService;
import es.jaes.cn_servicies.comment.CommentResponse;
import es.jaes.cn_servicies.comment.CommentService;
import es.jaes.cn_servicies.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CommentService commentService;
    private final ClubService clubService;
    private final AccessGuard accessGuard;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(java.security.Principal principal) {
        return ResponseEntity.ok(userService.findByUsername(principal.getName()));
    }

    @GetMapping
    public ResponseEntity<Page<UserResponse>> findAll(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean blocked,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(userService.findAll(q, role, blocked, pageable));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        // Endpoint de administracion: siempre autenticado, asi que la peticion
        // trae club. Si no lo trajera, require() revienta en vez de elegir uno.
        Club club = clubService.getById(TenantContext.require());
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request, club));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable UUID id, @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @PutMapping("/{id}/roles")
    public ResponseEntity<UserResponse> changeRoles(@PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest request) {
        return ResponseEntity.ok(userService.changeRole(id, request));
    }

    /**
     * La foto de una cuenta la cambia su dueno, o el administrador del club.
     *
     * <p>Esta ruta esta en {@code authenticated()} porque cada uno cambia la
     * suya; sin la comprobacion de abajo, eso significaba que cualquiera con un
     * id sobrescribia la foto de cualquier otro.
     */
    @PostMapping("/{id}/profile-photo")
    public ResponseEntity<UserResponse> uploadProfilePhoto(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        accessGuard.requireUserAccess(id);
        return ResponseEntity.ok(userService.uploadProfilePhoto(id, file));
    }

    /**
     * Cada uno cambia su contrasena, dando la actual.
     *
     * <p>Va antes que {@code /{id}/password} y esta suelta en
     * {@code SecurityConfig}: la regla general de {@code /api/users/**} es de
     * administrador, y sin la excepcion nadie podria cambiar la suya.
     */
    @PutMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(@Valid @RequestBody ChangeMyPasswordRequest request,
                                                 java.security.Principal principal) {
        userService.changeOwnPassword(principal.getName(),
                request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * El administrador fija la contrasena de una cuenta, sin saber la anterior.
     * Es la salida a una contrasena olvidada mientras no haya recuperacion por
     * correo.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/password")
    public ResponseEntity<Void> setPassword(@PathVariable UUID id,
                                            @Valid @RequestBody SetPasswordRequest request) {
        userService.setPassword(id, request.getPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * Bloquear y desbloquear: el administrador, cualquier cuenta; el editor,
     * solo cuentas de usuario. Ver
     * {@link AccessGuard#requireBlockAccess}.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    @PatchMapping("/{id}/block")
    public ResponseEntity<UserResponse> block(@PathVariable UUID id) {
        accessGuard.requireBlockAccess(id);
        return ResponseEntity.ok(userService.blockUser(id));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    @PatchMapping("/{id}/unblock")
    public ResponseEntity<UserResponse> unblock(@PathVariable UUID id) {
        accessGuard.requireBlockAccess(id);
        return ResponseEntity.ok(userService.unblockUser(id));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<List<CommentResponse>> getCommentsByUser(@PathVariable UUID id) {
        return ResponseEntity.ok(commentService.findByAuthor(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}