package es.jaes.cn_servicies.athlete;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AthleteRepository extends JpaRepository<Athlete, UUID> {
    boolean existsByDni(String dni);
}