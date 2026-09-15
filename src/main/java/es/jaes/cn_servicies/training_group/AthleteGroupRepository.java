package es.jaes.cn_servicies.training_group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AthleteGroupRepository extends JpaRepository<AthleteGroup, UUID> {

    /**
     * <b>La consulta sobre la que se apoya toda la Fase 2:</b> quienes eran
     * miembros de un grupo en una fecha dada.
     *
     * <p>Los dos extremos van incluidos. {@code joinedOn <= fecha} porque el dia
     * del alta ya se entrena, y {@code leftOn >= fecha} porque {@code leftOn} es
     * el ultimo dia de pertenencia, no el primero fuera. Cambiar cualquiera de
     * los dos por un estricto desplaza un dia todas las listas de asistencia.
     */
    @Query("SELECT m FROM AthleteGroup m"
            + " WHERE m.trainingGroup.id = :groupId"
            + "   AND m.joinedOn <= :date"
            + "   AND (m.leftOn IS NULL OR m.leftOn >= :date)")
    List<AthleteGroup> findMembersOn(UUID groupId, LocalDate date);

    /** Pertenencias abiertas de un atleta, en cualquier grupo. */
    List<AthleteGroup> findByAthleteIdAndLeftOnIsNull(UUID athleteId);

    /** La abierta de este atleta en este grupo, si la hay. Como mucho puede haber una. */
    Optional<AthleteGroup> findByAthleteIdAndTrainingGroupIdAndLeftOnIsNull(
            UUID athleteId, UUID groupId);

    /** Historico completo de un atleta, lo mas reciente primero. */
    List<AthleteGroup> findByAthleteIdOrderByJoinedOnDesc(UUID athleteId);

    /** Historico de un atleta acotado a una temporada. */
    @Query("SELECT m FROM AthleteGroup m"
            + " WHERE m.athlete.id = :athleteId"
            + "   AND m.trainingGroup.season.id = :seasonId"
            + " ORDER BY m.joinedOn DESC")
    List<AthleteGroup> findByAthleteAndSeason(UUID athleteId, UUID seasonId);

    List<AthleteGroup> findByTrainingGroupIdOrderByJoinedOnDesc(UUID groupId);

    long countByTrainingGroupIdAndLeftOnIsNull(UUID groupId);

    /**
     * Conteo de miembros abiertos de varios grupos de una vez.
     *
     * <p>Una consulta y no una por grupo: el listado de la 1.4 pinta catorce
     * grupos con su conteo, y preguntarlo de uno en uno son catorce viajes a la
     * base para dibujar una tabla.
     */
    @Query("SELECT m.trainingGroup.id, COUNT(m) FROM AthleteGroup m"
            + " WHERE m.leftOn IS NULL AND m.trainingGroup.id IN :groupIds"
            + " GROUP BY m.trainingGroup.id")
    List<Object[]> countOpenByGroup(Collection<UUID> groupIds);

    /**
     * Atletas que eran miembros de alguno de esos grupos en una fecha.
     *
     * <p>El criterio de pertenencia es el mismo de {@link #findMembersOn}, con los
     * dos extremos incluidos. Si alguien cambia uno, tiene que cambiar el otro:
     * si no, un entrenador veria en el roster a un nadador cuya ficha no puede
     * abrir, o al reves.
     */
    @Query("SELECT DISTINCT m.athlete.id FROM AthleteGroup m"
            + " WHERE m.trainingGroup.id IN :groupIds"
            + "   AND m.joinedOn <= :date"
            + "   AND (m.leftOn IS NULL OR m.leftOn >= :date)")
    List<UUID> findAthleteIdsInGroupsOn(Collection<UUID> groupIds, LocalDate date);
}
