package es.jaes.cn_servicies.document_delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DocumentDeliveryRepository extends JpaRepository<DocumentDelivery, UUID> {

    /**
     * Todas las de un atleta, de todos los tipos. Cual manda depende del tipo —la
     * licencia de la temporada activa, el documento que mas lejos caduca— y se
     * decide en el servicio.
     */
    List<DocumentDelivery> findByAthleteIdOrderByDeliveredOnDesc(UUID athleteId);

    /**
     * Las de varios atletas de una vez. Lo usa el informe de documentacion
     * pendiente (bloque 3c): de uno en uno seria una consulta por atleta.
     */
    List<DocumentDelivery> findByAthleteIdIn(Collection<UUID> athleteIds);

    /** Si hay un permiso que empieza antes o el mismo dia de la salida y acaba despues o el mismo dia de la vuelta. */
    boolean existsByAthleteIdAndTypeAndValidFromLessThanEqualAndValidUntilGreaterThanEqual(
            UUID athleteId, DocumentDeliveryType type, LocalDate salida, LocalDate vuelta);
}
