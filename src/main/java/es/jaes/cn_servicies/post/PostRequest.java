package es.jaes.cn_servicies.post;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PostRequest {

    @NotBlank(message = "El título no puede estar vacío")
    private String title;

    @NotBlank(message = "El contenido no puede estar vacío")
    private String content;

    private PostStatus status;
}