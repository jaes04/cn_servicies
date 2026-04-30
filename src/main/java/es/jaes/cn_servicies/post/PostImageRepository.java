package es.jaes.cn_servicies.post;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostImageRepository extends JpaRepository<PostImage, UUID> {

    List<PostImage> findByPostIdOrderByCreatedAtAsc(UUID postId);
}