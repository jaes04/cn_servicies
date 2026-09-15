package es.jaes.cn_servicies.athlete;

import es.jaes.cn_servicies.club.Club;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.UUID;

public interface AthleteRepository extends JpaRepository<Athlete, UUID>, JpaSpecificationExecutor<Athlete> {

    boolean existsByClubAndDni(Club club, String dni);

    /**
     * Si ya hay en el club una ficha de alguien con ese nombre, apellidos y fecha
     * de nacimiento. Es lo que detecta el duplicado cuando no hay documento de
     * identidad con el que compararlo (bloque 3b).
     */
    boolean existsByClubAndFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDate(
            Club club, String firstName, String lastName, LocalDate birthDate);

    /** Lo mismo sin contar a una ficha concreta: la que se esta editando. */
    boolean existsByClubAndFirstNameIgnoreCaseAndLastNameIgnoreCaseAndBirthDateAndIdNot(
            Club club, String firstName, String lastName, LocalDate birthDate, UUID id);
}
