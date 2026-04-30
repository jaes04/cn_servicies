package es.jaes.cn_servicies.athlete;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/athletes")
@RequiredArgsConstructor
public class AthleteController {

    private final AthleteService athleteService;

    @GetMapping
    public ResponseEntity<List<AthleteResponse>> findAll() {
        return ResponseEntity.ok(athleteService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AthleteResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(athleteService.findById(id));
    }

    @PostMapping
    public ResponseEntity<AthleteResponse> create(@Valid @RequestBody AthleteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(athleteService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AthleteResponse> update(@PathVariable UUID id, @Valid @RequestBody AthleteRequest request) {
        return ResponseEntity.ok(athleteService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        athleteService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}