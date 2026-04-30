package es.jaes.cn_servicies.competition_result;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CompetitionResultRequest {

    @NotNull
    private UUID athleteId;

    @NotNull
    private LocalDate competitionDate;

    @NotNull
    private Integer distanceMeters;

    @NotNull
    private Stroke stroke;

    @NotNull
    private Integer poolLength;

    @NotNull
    @Positive
    private Long resultTimeMillis;

    private boolean partial = false;

    private UUID finalResultId;
}