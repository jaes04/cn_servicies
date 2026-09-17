package es.jaes.cn_servicies.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;

@Data
@Getter
public class LoginRequest {

    /**
     * Slug del club en el que se entra. Lo lleva configurado cada frontend: el
     * username es unico por club, y sin el club no se sabe de quien es.
     */
    @NotBlank(message = "El club no puede estar vacío")
    private String clubSlug;

    @NotBlank(message = "El username no puede estar vacío")
    private String username;

    @NotBlank(message = "La contraseña no puede estar vacía")
    private String password;
}
