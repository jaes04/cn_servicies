package es.jaes.cn_servicies.athlete;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AthleteResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private String dni;
    private Gender gender;
    private LocalDateTime createdAt;
}