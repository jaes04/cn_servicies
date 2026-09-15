package es.jaes.cn_servicies.document_delivery;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Informe de documentacion pendiente de la temporada activa (bloque 3c). */
@Data
public class PendingDocumentationResponse {

    /** Temporada contra la que se mide. Nulos si el club no tiene ninguna activa, y entonces la lista va vacia. */
    private UUID seasonId;
    private String seasonName;

    /** Cuantos atletas tienen algo pendiente. Es el numero del aviso. */
    private int total;

    /** Ordenados por nombre. */
    private List<PendingAthleteDocumentationResponse> athletes = new ArrayList<>();
}
