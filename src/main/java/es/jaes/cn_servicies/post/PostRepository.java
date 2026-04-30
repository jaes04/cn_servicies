package es.jaes.cn_servicies.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    Page<Post> findByStatus(PostStatus status, Pageable pageable);

    Optional<Post> findBySlug(String slug);

    @Query("SELECT p FROM Post p WHERE p.status != es.jaes.cn_servicies.post.PostStatus.DELETED")
    Page<Post> findAllActive(Pageable pageable);
}