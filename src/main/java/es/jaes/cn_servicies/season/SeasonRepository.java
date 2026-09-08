package es.jaes.cn_servicies.season;

import es.jaes.cn_servicies.club.Club;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonRepository extends JpaRepository<Season, UUID> {

    List<Season> findAllByOrderByStartDateDesc();

    Optional<Season> findByActiveTrue();

    boolean existsByClubAndName(Club club, String name);

    /**
     * Si el club ya tiene una temporada que se cruce con el intervalo dado.
     *
     * <p>La condicion de solapamiento entre dos intervalos es que cada uno
     * empiece antes de que el otro acabe: {@code inicio <= finNuevo} y
     * {@code fin >= inicioNuevo}. Ojo al orden de los parametros, que va
     * cruzado precisamente por eso.
     *
     * <p>Los extremos cuentan, y tienen que contar: una temporada que termina
     * el 31 de agosto y otra que empieza el 1 de septiembre no se solapan, pero
     * dos que compartan un solo dia si.
     */
    boolean existsByClubAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Club club, LocalDate finNuevo, LocalDate inicioNuevo);

    /** La misma comprobacion al editar, sin contarse a si misma. */
    boolean existsByClubAndIdNotAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Club club, UUID id, LocalDate finNuevo, LocalDate inicioNuevo);

    /** Cuantas tiene el club, para saber si hay que sembrar la primera. */
    long countByClub(Club club);

    /**
     * Apaga todas las del club en una sentencia. Acotado por {@code club} de
     * forma explicita y no por el filtro de Hibernate: los {@code @Modifying} no
     * pasan por el, asi que dejarlo implicito apagaria las de todos los clubes.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Season s SET s.active = false WHERE s.club = :club AND s.active = true")
    void deactivateAll(Club club);
}
