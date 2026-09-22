package es.jaes.cn_servicies.guardian;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.UUID;

class GuardianSpecification {

    /** Tutores vinculados a alguno de esos atletas. Es lo que acota el listado de un entrenador. */
    static Specification<Guardian> linkedToAnyAthlete(Collection<UUID> athleteIds) {
        return (root, query, cb) -> {
            Subquery<UUID> vinculados = query.subquery(UUID.class);
            Root<AthleteGuardian> link = vinculados.from(AthleteGuardian.class);
            vinculados.select(link.get("guardian").get("id"))
                    .where(link.get("athlete").get("id").in(athleteIds));
            return root.get("id").in(vinculados);
        };
    }

    static Specification<Guardian> nameDniOrEmailContains(String q) {
        String pattern = "%" + q.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("firstName")), pattern),
                cb.like(cb.lower(root.get("lastName")), pattern),
                cb.like(cb.lower(root.get("dni")), pattern),
                cb.like(cb.lower(root.get("email")), pattern)
        );
    }
}
