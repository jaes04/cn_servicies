package es.jaes.cn_servicies.post;

import org.springframework.data.jpa.domain.Specification;

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

    static Specification<Post> hasStatus(PostStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}