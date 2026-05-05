package es.jaes.cn_servicies.comment;

import es.jaes.cn_servicies.post.Post;
import es.jaes.cn_servicies.post.PostRepository;
import es.jaes.cn_servicies.user.User;
import es.jaes.cn_servicies.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public CommentResponse create(UUID postId, CommentRequest request, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post no encontrado"));

        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        Comment comment = new Comment();
        comment.setContent(request.getContent());
        comment.setPost(post);
        comment.setAuthor(author);

        return toResponse(commentRepository.saveAndFlush(comment));
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> findByPost(UUID postId) {
        if (!postRepository.existsById(postId)) {
            throw new EntityNotFoundException("Post no encontrado");
        }
        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId)
                .stream()
                .filter(c -> !c.isBlocked())
                .map(this::toResponse)
                .toList();
    }

    public CommentResponse blockComment(UUID commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comentario no encontrado"));
        comment.setBlocked(true);
        return toResponse(commentRepository.save(comment));
    }

    public CommentResponse unblockComment(UUID commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comentario no encontrado"));
        comment.setBlocked(false);
        return toResponse(commentRepository.save(comment));
    }

    private CommentResponse toResponse(Comment comment) {
        CommentResponse response = new CommentResponse();
        response.setId(comment.getId());
        response.setContent(comment.getContent());
        response.setAuthorUsername(comment.getAuthor().getUsername());
        response.setPostId(comment.getPost().getId());
        response.setBlocked(comment.isBlocked());
        response.setCreatedAt(comment.getCreatedAt());
        return response;
    }
}