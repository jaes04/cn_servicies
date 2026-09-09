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
}
