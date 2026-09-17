package es.jaes.cn_servicies.user;


import es.jaes.cn_servicies.club.Club;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /**
     * Ambiguo desde que el username es unico por club: puede existir un 'admin'
     * en cada uno. Solo es fiable dentro de una peticion autenticada, donde el
     * filtro de Hibernate y las policies lo acotan al club del token. Para
     * autenticar, o en cualquier sitio sin club en contexto, usa
     * {@link #findByClubIdAndUsername}.
     */
    Optional<User> findByUsername(String username);

    Optional<User> findByClubAndUsername(Club club, String username);

    /** La cuenta de un club concreto. Es la que usa la autenticacion. */
    Optional<User> findByClubIdAndUsername(UUID clubId, String username);

    Optional<User> findByEmail(String email);

    /** Ambiguo por el mismo motivo que {@link #findByUsername}. */
    boolean existsByUsername(String username);

    boolean existsByClubAndUsername(Club club, String username);

    boolean existsByEmail(String email);
}