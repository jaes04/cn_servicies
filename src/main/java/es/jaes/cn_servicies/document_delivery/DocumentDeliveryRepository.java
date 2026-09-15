package es.jaes.cn_servicies.document_delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DocumentDeliveryRepository extends JpaRepository<DocumentDelivery, UUID> {

    List<DocumentDelivery> findByAthleteIdOrderByDeliveredOnDesc(UUID athleteId);

    /**
     * Todos los de un tipo, sin ordenar: cual manda depende del tipo —la licencia
     * de la temporada activa, el documento que mas lejos caduca— y se decide en el
     * servicio. Un atleta tiene pocos, no merece la pena una consulta por caso.
     */
    List<DocumentDelivery> findByAthleteIdAndType(UUID athleteId, DocumentDeliveryType type);

    /** Si hay un permiso que empieza antes o el mismo dia de la salida y acaba despues o el mismo dia de la vuelta. */
    boolean existsByAthleteIdAndTypeAndValidFromLessThanEqualAndValidUntilGreaterThanEqual(
            UUID athleteId, DocumentDeliveryType type, LocalDate salida, LocalDate vuelta);
}
