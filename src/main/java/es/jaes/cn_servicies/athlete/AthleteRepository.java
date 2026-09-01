package es.jaes.cn_servicies.athlete;

import es.jaes.cn_servicies.club.Club;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface AthleteRepository extends JpaRepository<Athlete, UUID>, JpaSpecificationExecutor<Athlete> {

    boolean existsByClubAndDni(Club club, String dni);
}