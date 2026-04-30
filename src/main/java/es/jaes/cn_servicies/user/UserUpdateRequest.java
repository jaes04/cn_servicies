package es.jaes.cn_servicies.user;

import lombok.Data;

import java.util.Set;

@Data
public class UserUpdateRequest {
    private Set<RoleName> roles;
    private Boolean enabled;
}
