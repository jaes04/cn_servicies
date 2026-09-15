package es.jaes.cn_servicies.training_group;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Un entrenador ayudante de un grupo, tal como sale en la respuesta. Nada mas que identificarlo. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupCoachResponse {

    private UUID id;
    private String username;
}
