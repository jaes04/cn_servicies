package es.jaes.cn_servicies.athlete;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GenderRepository extends JpaRepository<GenderEntity, Long> {
    Optional<GenderEntity> findByName(Gender name);
}