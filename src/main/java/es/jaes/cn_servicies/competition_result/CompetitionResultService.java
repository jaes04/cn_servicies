package es.jaes.cn_servicies.competition_result;

import es.jaes.cn_servicies.athlete.Athlete;
import es.jaes.cn_servicies.athlete.AthleteService;
import es.jaes.cn_servicies.athlete_link.UserAthleteRepository;
import es.jaes.cn_servicies.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CompetitionResultService {

    private final CompetitionResultRepository resultRepository;
    private final AthleteService athleteService;
    private final UserAthleteRepository userAthleteRepository;
    private final UserRepository userRepository;

    public CompetitionResultResponse create(CompetitionResultRequest request) {
        validateDistanceAndPool(request);
        if (request.isPartial() && request.getFinalResultId() == null) {
            throw new IllegalArgumentException("Un resultado parcial debe referenciar el resultado final");
        }

        CompetitionResult result = new CompetitionResult();
        result.setAthlete(athleteService.findOrThrow(request.getAthleteId()));
        result.setCompetitionDate(request.getCompetitionDate());
        result.setDistanceMeters(request.getDistanceMeters());
        result.setStroke(request.getStroke());
        result.setPoolLength(request.getPoolLength());
        result.setResultTimeMillis(request.getResultTimeMillis());
        result.setPartial(request.isPartial());

        if (request.isPartial()) {
            CompetitionResult finalResult = findOrThrow(request.getFinalResultId());
            if (finalResult.isPartial()) {
                throw new IllegalArgumentException("El resultado final no puede ser a su vez un parcial");
            }
            result.setFinalResult(finalResult);
        }

        return toResponse(resultRepository.save(result));
    }

    @Transactional(readOnly = true)
    public CompetitionResultResponse findById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<CompetitionResultResponse> findAll(
            String q, Stroke stroke, Integer distanceMeters, Integer poolLength, Boolean partial,
            Pageable pageable) {
        Specification<CompetitionResult> spec = Specification.where(null);
        if (q != null && !q.isBlank()) spec = spec.and(CompetitionResultSpecification.athleteNameContains(q));
        if (stroke != null) spec = spec.and(CompetitionResultSpecification.hasStroke(stroke));
        if (distanceMeters != null) spec = spec.and(CompetitionResultSpecification.hasDistance(distanceMeters));
        if (poolLength != null) spec = spec.and(CompetitionResultSpecification.hasPoolLength(poolLength));
        if (partial != null) spec = spec.and(CompetitionResultSpecification.isPartial(partial));
        return resultRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<CompetitionResultResponse> findByAthlete(UUID athleteId) {
        return resultRepository.findByAthleteId(athleteId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<CompetitionResultResponse> findByCurrentUser(
            String username, Stroke stroke, Integer distanceMeters, Integer poolLength, Boolean partial,
            Pageable pageable) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        var athleteIds = userAthleteRepository.findByUserId(user.getId()).stream()
                .map(ua -> ua.getAthlete().getId())
                .toList();
        if (athleteIds.isEmpty()) return Page.empty(pageable);

        Specification<CompetitionResult> spec = CompetitionResultSpecification.athleteIdIn(athleteIds);
        if (stroke != null) spec = spec.and(CompetitionResultSpecification.hasStroke(stroke));
        if (distanceMeters != null) spec = spec.and(CompetitionResultSpecification.hasDistance(distanceMeters));
        if (poolLength != null) spec = spec.and(CompetitionResultSpecification.hasPoolLength(poolLength));
        if (partial != null) spec = spec.and(CompetitionResultSpecification.isPartial(partial));
        return resultRepository.findAll(spec, pageable).map(this::toResponse);
    }

    public CompetitionResultResponse update(UUID id, CompetitionResultRequest request) {
        validateDistanceAndPool(request);
        CompetitionResult result = findOrThrow(id);

        result.setAthlete(athleteService.findOrThrow(request.getAthleteId()));
        result.setCompetitionDate(request.getCompetitionDate());
        result.setDistanceMeters(request.getDistanceMeters());
        result.setStroke(request.getStroke());
        result.setPoolLength(request.getPoolLength());
        result.setResultTimeMillis(request.getResultTimeMillis());
        result.setPartial(request.isPartial());

        if (request.isPartial()) {
            if (request.getFinalResultId() == null) {
                throw new IllegalArgumentException("Un resultado parcial debe referenciar el resultado final");
            }
            CompetitionResult finalResult = findOrThrow(request.getFinalResultId());
            if (finalResult.isPartial()) {
                throw new IllegalArgumentException("El resultado final no puede ser a su vez un parcial");
            }
            result.setFinalResult(finalResult);
        } else {
            result.setFinalResult(null);
        }

        return toResponse(resultRepository.save(result));
    }

    public void softDelete(UUID id) {
        CompetitionResult result = findOrThrow(id);
        result.setDeletedAt(LocalDateTime.now());
        resultRepository.save(result);
    }

    private CompetitionResult findOrThrow(UUID id) {
        return resultRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Resultado no encontrado"));
    }

    private static final List<Integer> OFFICIAL_DISTANCES = List.of(50, 100, 200, 400, 800, 1500);

    private void validateDistanceAndPool(CompetitionResultRequest request) {
        if (!List.of(25, 50).contains(request.getPoolLength())) {
            throw new IllegalArgumentException("La longitud de piscina debe ser 25 o 50 metros");
        }
        int dist = request.getDistanceMeters();
        if (request.isPartial()) {
            if (dist < 50 || dist > 1450 || dist % 50 != 0) {
                throw new IllegalArgumentException("Un parcial debe ser múltiplo de 50 entre 50 y 1450 metros");
            }
        } else if (!OFFICIAL_DISTANCES.contains(dist)) {
            throw new IllegalArgumentException("Distancia no válida. Valores permitidos: 50, 100, 200, 400, 800, 1500");
        }
    }

    private CompetitionResultResponse toResponse(CompetitionResult result) {
        CompetitionResultResponse response = new CompetitionResultResponse();
        response.setId(result.getId());
        Athlete athlete = result.getAthlete();
        response.setAthleteId(athlete.getId());
        response.setAthleteFullName(athlete.getFirstName() + " " + athlete.getLastName());
        response.setCompetitionDate(result.getCompetitionDate());
        response.setDistanceMeters(result.getDistanceMeters());
        response.setStroke(result.getStroke());
        response.setPoolLength(result.getPoolLength());
        response.setResultTimeMillis(result.getResultTimeMillis());
        response.setPartial(result.isPartial());
        response.setFinalResultId(result.getFinalResult() != null ? result.getFinalResult().getId() : null);
        response.setCreatedAt(result.getCreatedAt());
        return response;
    }
}