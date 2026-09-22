package es.jaes.cn_servicies.guardian;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Alta de un tutor para un atleta que ya existe.
 *
 * <p>Sin consentimientos, a diferencia de {@link AthleteGuardianRequest}: ahi van
 * juntos porque crear la ficha de un menor ya es tratamiento. Aqui la ficha ya
 * existe y tiene su base legal; los consentimientos que aporte este tutor se
 * registran despues, por {@code /api/consents}.
 */
@Data
public class GuardianLinkRequest {

    @NotNull
    @Valid
    private GuardianRequest guardian;

    @NotNull
    private GuardianRelationship relationship;
}
