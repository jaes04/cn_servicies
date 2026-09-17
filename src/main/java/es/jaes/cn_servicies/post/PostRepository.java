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
     *
     * <p>Lleva el club porque lo sirve un endpoint anonimo: sin token no hay
     * filtro de Hibernate y las policies estan en modo {@code public}, asi que
     * lo unico que impide abrir la noticia de otro club es esta condicion.
     */
    Optional<Post> findByIdAndStatusAndClubId(UUID id, PostStatus status, UUID clubId);
}