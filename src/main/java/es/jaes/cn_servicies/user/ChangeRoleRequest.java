package es.jaes.cn_servicies.user;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChangeRoleRequest {

    @NotNull(message = "El rol no puede ser nulo")
    private RoleName role;
}