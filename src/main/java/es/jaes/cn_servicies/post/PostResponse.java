package es.jaes.cn_servicies.post;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class PostResponse {
    private UUID id;
    private String title;
    private String content;
    private String slug;
    private PostStatus status;
    private String authorUsername;
    private List<String> imageUrls;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}