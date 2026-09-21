package es.jaes.cn_servicies.comment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    public ResponseEntity<CommentResponse> create(
            @PathVariable UUID postId,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.create(postId, request, userDetails.getUsername()));
    }

    /**
     * Sin parametro, lo que ve cualquiera: sin bloqueados ni borrados. Con
     * {@code includeBlocked=true}, tambien los bloqueados, que es como el
     * administrador o el editor encuentran lo que tienen que desbloquear. Es
     * opcional y no depende del rol a secas para que la vista del blog no
     * enseñe bloqueados solo porque quien mira es editor.
     */
    @GetMapping
    public ResponseEntity<List<CommentResponse>> listByPost(
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "false") boolean includeBlocked,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (includeBlocked && !moderates(userDetails)) {
            throw new AccessDeniedException("Solo quien modera ve los comentarios bloqueados");
        }
        return ResponseEntity.ok(commentService.findByPost(postId, includeBlocked));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID postId,
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        commentService.softDelete(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    @PatchMapping("/{id}/block")
    public ResponseEntity<CommentResponse> block(@PathVariable UUID postId, @PathVariable UUID id) {
        return ResponseEntity.ok(commentService.blockComment(id));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    @PatchMapping("/{id}/unblock")
    public ResponseEntity<CommentResponse> unblock(@PathVariable UUID postId, @PathVariable UUID id) {
        return ResponseEntity.ok(commentService.unblockComment(id));
    }

    private static boolean moderates(UserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_EDITOR"));
    }
}