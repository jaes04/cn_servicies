package es.jaes.cn_servicies.training_session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {

    /** Las sesiones del grupo en un rango, los dos extremos incluidos. */
    @Query("SELECT s FROM TrainingSession s"
            + " WHERE s.trainingGroup.id = :groupId"
            + "   AND s.date BETWEEN :from AND :to"
            + " ORDER BY s.date ASC, s.startTime ASC")
    List<TrainingSession> findByGroupBetween(UUID groupId, LocalDate from, LocalDate to);

    /**
     * Los pares (horario, fecha) que ya existen en el rango.
     *
     * <p>Es la consulta de la que vive la idempotencia: el generador la pide una
     * vez, la mete en un conjunto y con eso sabe que dias tiene que crear sin
     * preguntar a la base por cada uno.
     *
     * <p>Deja fuera las puntuales ({@code schedule IS NULL}), que no compiten con
     * ninguna generada: una competicion el martes no impide que se genere el
     * entrenamiento de ese martes.
     */
    @Query("SELECT s.schedule.id, s.date FROM TrainingSession s"
            + " WHERE s.trainingGroup.id = :groupId"
            + "   AND s.schedule IS NOT NULL"
            + "   AND s.date BETWEEN :from AND :to")
    List<Object[]> findGeneratedKeysBetween(UUID groupId, LocalDate from, LocalDate to);

    /**
     * Las sesiones programadas del club en un rango, de cualquier grupo. La usa
     * el alta de un cierre para tumbar lo que ya estaba generado.
     *
     * <p>Sin grupo en la condicion —un festivo es del club entero— y sin
     * {@code club_id} tampoco: lo pone el filtro de tenancy, y la policy debajo.
     *
     * <p>Solo las {@code SCHEDULED} a proposito: una ya cancelada conserva el
     * motivo que tenia, y una {@code DONE} significa que se entreno.
     */
    @Query("SELECT s FROM TrainingSession s"
            + " WHERE s.date BETWEEN :from AND :to"
            + "   AND s.status = es.jaes.cn_servicies.training_session.SessionStatus.SCHEDULED")
    List<TrainingSession> findScheduledBetween(LocalDate from, LocalDate to);
}
