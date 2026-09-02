package es.jaes.cn_servicies.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID>, JpaSpecificationExecutor<Post> {

    /**
     * El post se identifica por id. No hay findBySlug: el slug puede repetirse
     * entre clubes, asi que devolver un Optional seria mentir — con dos
     * coincidencias lanzaria NonUniqueResultException.
     */
    Optional<Post> findByIdAndStatus(UUID id, PostStatus status);
}