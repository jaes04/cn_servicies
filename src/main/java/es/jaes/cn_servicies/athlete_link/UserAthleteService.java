package es.jaes.cn_servicies.athlete_link;

import es.jaes.cn_servicies.athlete.Athlete;
import es.jaes.cn_servicies.athlete.AthleteService;
import es.jaes.cn_servicies.user.User;
import es.jaes.cn_servicies.user.UserRepository;
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
public class UserAthleteService {

    private static final int KEY_EXPIRATION_HOURS = 72;

    private final AthleteInviteKeyRepository inviteKeyRepository;
    private final UserAthleteRepository userAthleteRepository;
    private final AthleteService athleteService;
    private final UserRepository userRepository;

    public AthleteInviteKeyResponse generateKey(UUID athleteId, UserAthleteType type) {
        Athlete athlete = athleteService.findOrThrow(athleteId);

        AthleteInviteKey inviteKey = new AthleteInviteKey();
        inviteKey.setKeyValue(UUID.randomUUID().toString());
        inviteKey.setAthlete(athlete);
        inviteKey.setType(type);
        inviteKey.setExpiresAt(LocalDateTime.now().plusHours(KEY_EXPIRATION_HOURS));
        inviteKeyRepository.save(inviteKey);

        return toKeyResponse(inviteKey);
    }

    public UserAthleteResponse redeemKey(String keyValue, String username) {
        AthleteInviteKey inviteKey = inviteKeyRepository.findByKeyValue(keyValue)
                .orElseThrow(() -> new IllegalArgumentException("Key no válida"));

        if (inviteKey.isUsed()) {
            throw new IllegalStateException("Esta key ya ha sido utilizada");
        }
        if (inviteKey.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("La key ha expirado");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        if (userAthleteRepository.existsByUserIdAndAthleteId(user.getId(), inviteKey.getAthlete().getId())) {
            throw new IllegalStateException("Este usuario ya está vinculado a este atleta");
        }

        UserAthlete link = new UserAthlete();
        link.setUser(user);
        link.setAthlete(inviteKey.getAthlete());
        link.setType(inviteKey.getType());
        userAthleteRepository.save(link);

        inviteKey.setUsed(true);
        inviteKeyRepository.save(inviteKey);

        return toResponse(link);
    }

    @Transactional(readOnly = true)
    public List<UserAthleteResponse> findByAthlete(UUID athleteId) {
        return userAthleteRepository.findByAthleteId(athleteId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserAthleteResponse> findByUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        return userAthleteRepository.findByUserId(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserAthleteResponse> findTuteesByUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        return userAthleteRepository.findByUserIdAndType(user.getId(), UserAthleteType.TUTOR).stream()
                .map(this::toResponse)
                .toList();
    }

    private AthleteInviteKeyResponse toKeyResponse(AthleteInviteKey key) {
        AthleteInviteKeyResponse response = new AthleteInviteKeyResponse();
        response.setKey(key.getKeyValue());
        response.setAthleteId(key.getAthlete().getId());
        response.setAthleteFullName(key.getAthlete().getFirstName() + " " + key.getAthlete().getLastName());
        response.setType(key.getType());
        response.setExpiresAt(key.getExpiresAt());
        return response;
    }

    private UserAthleteResponse toResponse(UserAthlete link) {
        UserAthleteResponse response = new UserAthleteResponse();
        response.setId(link.getId());
        response.setUserId(link.getUser().getId());
        response.setUsername(link.getUser().getUsername());
        response.setAthleteId(link.getAthlete().getId());
        response.setAthleteFullName(link.getAthlete().getFirstName() + " " + link.getAthlete().getLastName());
        response.setType(link.getType());
        response.setCreatedAt(link.getCreatedAt());
        return response;
    }
}