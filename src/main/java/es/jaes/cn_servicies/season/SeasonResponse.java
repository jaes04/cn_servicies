package es.jaes.cn_servicies.season;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class SeasonResponse {

    private UUID id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean active;
}
