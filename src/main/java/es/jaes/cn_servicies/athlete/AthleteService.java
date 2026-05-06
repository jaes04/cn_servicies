package es.jaes.cn_servicies.athlete;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AthleteService {

    private final AthleteRepository athleteRepository;

    public AthleteResponse create(AthleteRequest request) {
        if (athleteRepository.existsByDni(request.getDni())) {
            throw new IllegalArgumentException("Ya existe un atleta con ese DNI");
        }
        Athlete athlete = new Athlete();
        athlete.setFirstName(request.getFirstName());
        athlete.setLastName(request.getLastName());
        athlete.setBirthDate(request.getBirthDate());
        athlete.setDni(request.getDni());
        return toResponse(athleteRepository.save(athlete));
    }

    @Transactional(readOnly = true)
    public AthleteResponse findById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<AthleteResponse> findAll(String q, Pageable pageable) {
        Specification<Athlete> spec = Specification.where(null);
        if (q != null && !q.isBlank()) spec = spec.and(AthleteSpecification.nameOrDniContains(q));
        return athleteRepository.findAll(spec, pageable).map(this::toResponse);
    }

    public AthleteResponse update(UUID id, AthleteRequest request) {
        Athlete athlete = findOrThrow(id);
        if (!athlete.getDni().equals(request.getDni()) && athleteRepository.existsByDni(request.getDni())) {
            throw new IllegalArgumentException("Ya existe un atleta con ese DNI");
        }
        athlete.setFirstName(request.getFirstName());
        athlete.setLastName(request.getLastName());
        athlete.setBirthDate(request.getBirthDate());
        athlete.setDni(request.getDni());
        return toResponse(athleteRepository.save(athlete));
    }

    public void softDelete(UUID id) {
        Athlete athlete = findOrThrow(id);
        athlete.setDeletedAt(LocalDateTime.now());
        athleteRepository.save(athlete);
    }

    public Athlete findOrThrow(UUID id) {
        return athleteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Atleta no encontrado"));
    }

    private AthleteResponse toResponse(Athlete athlete) {
        AthleteResponse response = new AthleteResponse();
        response.setId(athlete.getId());
        response.setFirstName(athlete.getFirstName());
        response.setLastName(athlete.getLastName());
        response.setBirthDate(athlete.getBirthDate());
        response.setDni(athlete.getDni());
        response.setCreatedAt(athlete.getCreatedAt());
        return response;
    }
}