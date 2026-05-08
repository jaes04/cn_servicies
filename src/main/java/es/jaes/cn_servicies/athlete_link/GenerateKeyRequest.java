package es.jaes.cn_servicies.athlete_link;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateKeyRequest {

    @NotNull(message = "El tipo es obligatorio")
    private UserAthleteType type;
}