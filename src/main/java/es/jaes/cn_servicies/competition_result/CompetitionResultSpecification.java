package es.jaes.cn_servicies.competition_result;

import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

class CompetitionResultSpecification {

    static Specification<CompetitionResult> athleteIdIn(List<UUID> athleteIds) {
        return (root, query, cb) -> root.get("athlete").get("id").in(athleteIds);
    }

    static Specification<CompetitionResult> athleteNameContains(String q) {
        String pattern = "%" + q.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("athlete").get("firstName")), pattern),
                cb.like(cb.lower(root.get("athlete").get("lastName")), pattern)
        );
    }

    static Specification<CompetitionResult> hasStroke(Stroke stroke) {
        return (root, query, cb) -> cb.equal(root.get("stroke"), stroke);
    }

    static Specification<CompetitionResult> hasDistance(Integer distance) {
        return (root, query, cb) -> cb.equal(root.get("distanceMeters"), distance);
    }

    static Specification<CompetitionResult> hasPoolLength(Integer poolLength) {
        return (root, query, cb) -> cb.equal(root.get("poolLength"), poolLength);
    }

    static Specification<CompetitionResult> isPartial(boolean partial) {
        return (root, query, cb) -> cb.equal(root.get("partial"), partial);
    }
}