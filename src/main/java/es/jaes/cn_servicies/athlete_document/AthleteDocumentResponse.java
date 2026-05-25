package es.jaes.cn_servicies.athlete_document;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
public class AthleteDocumentResponse {
    private UUID id;
    private String title;
    private AthleteDocumentType type;
    private String originalFilename;
    private UUID athleteId;
    private String athleteFullName;
    private UUID uploadedById;
    private String uploadedByUsername;
    private LocalDateTime createdAt;
}