package es.jaes.cn_servicies.guardian;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Ficha de un tutor en el listado del club.
 *
 * <p>No expone la cuenta, solo si existe: {@code hasAccount} dice si el tutor
 * ya canjeo su invitacion, que es lo que el club necesita saber, sin sacar datos
 * de acceso.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuardianResponse {

    private UUID id;
    private String firstName;
    private String lastName;
    private String dni;
    private String email;
    private String phone;
    private boolean hasAccount;
    private List<LinkedAthlete> athletes;
    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkedAthlete {
        private UUID athleteId;
        private String athleteFullName;
        private GuardianRelationship relationship;
    }
}
