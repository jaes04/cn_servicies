package es.jaes.cn_servicies.user;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Data
public class UserResponse {
    private UUID id;
    private String username;
    private String email;
    private boolean blocked;
    private Set<String> roles;
    private LocalDateTime createdAt;
    private String profilePhoto;
}