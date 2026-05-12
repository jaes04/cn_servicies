package es.jaes.cn_servicies.comment;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CommentResponse {
    private UUID id;
    private String content;
    private String authorUsername;
    private UUID postId;
    private String postTitle;
    private boolean blocked;
    private LocalDateTime createdAt;
}