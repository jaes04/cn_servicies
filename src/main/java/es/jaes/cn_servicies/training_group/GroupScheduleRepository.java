package es.jaes.cn_servicies.training_group;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface GroupScheduleRepository extends JpaRepository<GroupSchedule, UUID> {

    /**
     * Todos los horarios vivos de un grupo.
     *
     * <p><b>Sin {@code ORDER BY} a proposito.</b> El dia de la semana se guarda
     * como texto, asi que ordenarlo en SQL da FRIDAY, MONDAY, SATURDAY... El
     * orden que espera cualquiera —lunes primero— es el del propio
     * {@link java.time.DayOfWeek}, y ese solo se consigue ordenando en Java.
     * Lo hace {@code GroupScheduleService}.
     */
    List<GroupSchedule> findByTrainingGroupId(UUID groupId);

    /**
     * Los que estan en vigor ese dia. Los dos extremos incluidos:
     * {@code validUntil} es el ultimo dia de vigencia, no el primero fuera.
     *
     * <p>Es la consulta de la que tirara el generador de sesiones de la 2.2.
     */
    @Query("SELECT h FROM GroupSchedule h"
            + " WHERE h.trainingGroup.id = :groupId"
            + "   AND h.validFrom <= :date"
            + "   AND (h.validUntil IS NULL OR h.validUntil >= :date)")
    List<GroupSchedule> findInForceOn(UUID groupId, LocalDate date);
}
