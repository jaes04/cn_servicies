package es.jaes.cn_servicies.season;

import es.jaes.cn_servicies.club.Club;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonRepository extends JpaRepository<Season, UUID> {

    List<Season> findAllByOrderByStartDateDesc();

    Optional<Season> findByActiveTrue();

    boolean existsByClubAndName(Club club, String name);

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
