package es.jaes.cn_servicies.training_session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ClubClosureRepository extends JpaRepository<ClubClosure, UUID> {

    /**
     * Los cierres que tocan el rango, aunque sea un solo dia.
     *
     * <p>Sirve para las dos cosas: el listado del calendario y la consulta que
     * hace el generador antes de materializar. El generador la pide <b>una vez
     * por rango</b> y decide dia a dia en memoria; preguntarlo por cada dia
     * serian cuarenta y dos viajes para generar seis semanas.
     *
     * <p>Sin {@code club_id} en la condicion a proposito: la entidad lleva el
     * filtro de tenancy y la tabla su policy, asi que el club lo pone el
     * aspecto. Escribirlo aqui a mano seria la tercera copia de la misma regla.
     */
    @Query("SELECT c FROM ClubClosure c"
            + " WHERE c.startDate <= :to AND c.endDate >= :from"
            + " ORDER BY c.startDate ASC")
    List<ClubClosure> findOverlapping(LocalDate from, LocalDate to);
}
