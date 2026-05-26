package es.jaes.cn_servicies.athlete;

import org.springframework.data.jpa.domain.Specification;

class AthleteSpecification {

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