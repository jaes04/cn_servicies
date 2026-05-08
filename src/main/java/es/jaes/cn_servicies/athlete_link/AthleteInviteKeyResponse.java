package es.jaes.cn_servicies.athlete_link;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AthleteInviteKeyResponse {

    private String key;
    private UUID athleteId;
    private String athleteFullName;
    private UserAthleteType type;
    private LocalDateTime expiresAt;
}