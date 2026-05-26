package es.jaes.cn_servicies.athlete_link;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/athlete-links")
@RequiredArgsConstructor
public class UserAthleteController {

    private final UserAthleteService userAthleteService;

    @PostMapping("/{athleteId}/key")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICAL_STAFF')")
    public ResponseEntity<AthleteInviteKeyResponse> generateKey(
            @PathVariable UUID athleteId,
            @Valid @RequestBody GenerateKeyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userAthleteService.generateKey(athleteId, request.getType()));
    }

    @PostMapping("/redeem")
    public ResponseEntity<UserAthleteResponse> redeemKey(
            @Valid @RequestBody RedeemKeyRequest request,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userAthleteService.redeemKey(request.getKey(), principal.getName()));
    }

    @GetMapping("/my-athletes")
    public ResponseEntity<List<UserAthleteResponse>> myAthletes(Principal principal) {
        return ResponseEntity.ok(userAthleteService.findByUser(principal.getName()));
    }

    @GetMapping("/my-tutees")
    public ResponseEntity<List<UserAthleteResponse>> myTutees(Principal principal) {
        return ResponseEntity.ok(userAthleteService.findTuteesByUser(principal.getName()));
    }

    @GetMapping("/by-athlete/{athleteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICAL_STAFF')")
    public ResponseEntity<List<UserAthleteResponse>> byAthlete(@PathVariable UUID athleteId) {
        return ResponseEntity.ok(userAthleteService.findByAthlete(athleteId));
    }
}