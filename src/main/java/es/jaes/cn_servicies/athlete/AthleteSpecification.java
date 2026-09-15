package es.jaes.cn_servicies.athlete;

import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.UUID;

class AthleteSpecification {

    static Specification<Athlete> idIn(Collection<UUID> ids) {
        return (root, query, cb) -> root.get("id").in(ids);
    }

    static Specification<Athlete> nameOrDniContains(String q) {
        String pattern = "%" + q.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("firstName")), pattern),
                cb.like(cb.lower(root.get("lastName")), pattern),
                cb.like(cb.lower(root.get("dni")), pattern)
        );
    }

    static Specification<Athlete> hasGender(Gender gender) {
        return (root, query, cb) -> cb.equal(root.get("gender").get("name"), gender);
    }
}
