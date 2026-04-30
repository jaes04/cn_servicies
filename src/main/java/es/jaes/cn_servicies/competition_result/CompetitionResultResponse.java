package es.jaes.cn_servicies.competition_result;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CompetitionResultResponse {
    private UUID id;
    private UUID athleteId;
    private String athleteFullName;
    private LocalDate competitionDate;
    private Integer distanceMeters;
    private Stroke stroke;
    private Integer poolLength;
    private Long resultTimeMillis;
    private boolean partial;
    private UUID finalResultId;
    private LocalDateTime createdAt;
}