package es.jaes.cn_servicies.user;

import org.springframework.data.jpa.domain.Specification;

class UserSpecification {

    static Specification<User> usernameOrEmailContains(String q) {
        String pattern = "%" + q.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("username")), pattern),
                cb.like(cb.lower(root.get("email")), pattern)
        );
    }

    static Specification<User> hasRole(String roleName) {
        return (root, query, cb) -> cb.equal(root.join("roles").get("name").as(String.class), roleName);
    }

    static Specification<User> isBlocked(boolean blocked) {
        return (root, query, cb) -> cb.equal(root.get("blocked"), blocked);
    }
}