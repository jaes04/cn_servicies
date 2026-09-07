package es.jaes.cn_servicies.guardian;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * Tutor y consentimientos que acompanan al alta de un atleta.
 *
 * <p>Van juntos y en la misma peticion porque crear la ficha de un menor ya es
 * tratamiento de sus datos: si el consentimiento llegara despues, habria un
 * intervalo con los datos de un menor tratados sin base legal.
 */
@Data
public class AthleteGuardianRequest {

    @NotNull
    @Valid
    private GuardianRequest guardian;

    @NotNull
    private GuardianRelationship relationship;

    @NotNull
    private ConsentEvidenceType evidenceType;

    @NotNull
    @PastOrPresent(message = "La fecha del consentimiento no puede ser futura")
    private LocalDate decisionDate;

    @Size(max = 100)
    private String evidenceRef;

    /**
     * Tratamiento de datos. <b>Tiene que ser true</b>: es el consentimiento sin
     * el cual no hay alta posible. Se comprueba en el servicio, no aqui, para
     * que el mensaje explique el motivo en lugar de decir "debe ser verdadero".
     */
    @NotNull
    private Boolean dataProcessing;

    /**
     * Imagen. Hay que responder, pero <b>un "no" es respuesta valida</b> y no
     * impide el alta: es una finalidad distinta y se revoca por separado. Objeto
     * y no primitivo justamente para distinguir "dijo que no" de "no contesto".
     */
    @NotNull
    private Boolean image;
}
