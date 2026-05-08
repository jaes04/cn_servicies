package es.jaes.cn_servicies.athlete_link;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class UserAthleteResponse {

    private UUID id;
    private UUID userId;
    private String username;
    private UUID athleteId;
    private String athleteFullName;
    private UserAthleteType type;
    private LocalDateTime createdAt;
}