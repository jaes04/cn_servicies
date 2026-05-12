package es.jaes.cn_servicies.comment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @GetMapping
    public ResponseEntity<List<CommentResponse>> listByPost(@PathVariable UUID postId) {
        return ResponseEntity.ok(commentService.findByPost(postId));
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
}