package es.jaes.cn_servicies.competition_result;

import es.jaes.cn_servicies.athlete.Athlete;
import es.jaes.cn_servicies.athlete.AthleteService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
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
    public List<CompetitionResultResponse> findAll() {
        return resultRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CompetitionResultResponse> findByAthlete(UUID athleteId) {
        return resultRepository.findByAthleteId(athleteId).stream().map(this::toResponse).toList();
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

    private void validateDistanceAndPool(CompetitionResultRequest request) {
        if (!List.of(25, 50).contains(request.getPoolLength())) {
            throw new IllegalArgumentException("La longitud de piscina debe ser 25 o 50 metros");
        }
        if (!List.of(50, 100, 200, 400, 800, 1500).contains(request.getDistanceMeters())) {
            throw new IllegalArgumentException("Distancia no válida");
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