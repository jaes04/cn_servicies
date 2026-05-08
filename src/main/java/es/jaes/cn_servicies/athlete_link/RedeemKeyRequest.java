package es.jaes.cn_servicies.athlete_link;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RedeemKeyRequest {

    @NotBlank(message = "La key es obligatoria")
    private String key;
}