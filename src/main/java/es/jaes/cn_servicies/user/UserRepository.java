package es.jaes.cn_servicies.user;


import es.jaes.cn_servicies.club.Club;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /**
     * Ambiguo desde que el username es unico por club: puede existir un 'admin'
     * en cada uno. Solo es fiable mientras haya un unico club. La tarea 0.3 lo
     * sustituye por la variante acotada, cuando el login resuelva el club antes
     * de autenticar.
     */
    Optional<User> findByUsername(String username);

    Optional<User> findByClubAndUsername(Club club, String username);

    Optional<User> findByEmail(String email);

    /** Ambiguo por el mismo motivo que {@link #findByUsername}. */
    boolean existsByUsername(String username);

    boolean existsByClubAndUsername(Club club, String username);

    boolean existsByEmail(String email);
}