package es.jaes.cn_servicies.guardian;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AthleteGuardianRepository extends JpaRepository<AthleteGuardian, UUID> {

    List<AthleteGuardian> findByAthleteId(UUID athleteId);

    /**
     * Los vinculos de una pagina de tutores, con su atleta, en una sola consulta.
     *
     * <p>El {@code deletedAt IS NULL} va explicito: el {@code @SQLRestriction} de
     * {@code Athlete} no se aplica al recorrer un {@code @ManyToOne}, y sin esta
     * condicion el listado seguiria nombrando a un atleta dado de baja.
     */
    @Query("SELECT ag FROM AthleteGuardian ag JOIN FETCH ag.athlete a"
            + " WHERE ag.guardian.id IN :guardianIds AND a.deletedAt IS NULL")
    List<AthleteGuardian> findActiveByGuardianIdIn(@Param("guardianIds") Collection<UUID> guardianIds);

    List<AthleteGuardian> findByGuardianId(UUID guardianId);

    boolean existsByAthleteIdAndGuardianId(UUID athleteId, UUID guardianId);
}
