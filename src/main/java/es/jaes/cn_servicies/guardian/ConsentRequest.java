package es.jaes.cn_servicies.guardian;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class ConsentRequest {

    @NotNull
    private UUID guardianId;

    @NotNull
    private ConsentType type;

    /** Objeto y no primitivo: que falte en el cuerpo tiene que ser un 400, no un "false" silencioso. */
    @NotNull
    private Boolean granted;

    @NotNull
    @PastOrPresent(message = "La fecha del consentimiento no puede ser futura")
    private LocalDate decisionDate;

    @NotNull
    private ConsentEvidenceType evidenceType;

    @Size(max = 100)
    private String evidenceRef;
}
