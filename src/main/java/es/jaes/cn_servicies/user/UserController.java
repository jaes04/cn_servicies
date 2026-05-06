package es.jaes.cn_servicies.user;

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

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

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
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable UUID id, @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @PutMapping("/{id}/roles")
    public ResponseEntity<UserResponse> changeRoles(@PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest request) {
        return ResponseEntity.ok(userService.changeRole(id, request));
    }

    @PostMapping("/{id}/profile-photo")
    public ResponseEntity<UserResponse> uploadProfilePhoto(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userService.uploadProfilePhoto(id, file));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/block")
    public ResponseEntity<UserResponse> block(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.blockUser(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/unblock")
    public ResponseEntity<UserResponse> unblock(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.unblockUser(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}