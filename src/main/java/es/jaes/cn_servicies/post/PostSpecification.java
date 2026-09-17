package es.jaes.cn_servicies.post;

import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

class PostSpecification {

    static Specification<Post> titleOrContentContains(String q) {
        String pattern = "%" + q.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("content")), pattern)
        );
    }

    static Specification<Post> hasAuthor(String username) {
        return (root, query, cb) -> cb.equal(root.get("author").get("username"), username);
    }

    /**
     * Explicito, para las consultas anonimas: sin token no hay filtro de
     * Hibernate y las policies lo dejan ver todo.
     */
    static Specification<Post> belongsToClub(UUID clubId) {
        return (root, query, cb) -> cb.equal(root.get("club").get("id"), clubId);
    }

    static Specification<Post> hasStatus(PostStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}