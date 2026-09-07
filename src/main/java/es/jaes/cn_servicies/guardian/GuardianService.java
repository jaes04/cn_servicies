package es.jaes.cn_servicies.guardian;

import es.jaes.cn_servicies.athlete.Athlete;
import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.club.ClubService;
import es.jaes.cn_servicies.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class GuardianService {

    private final GuardianRepository guardianRepository;
    private final AthleteGuardianRepository athleteGuardianRepository;
    private final ClubService clubService;

    /**
     * Devuelve la ficha del tutor con ese DNI en el club, y la crea si no
     * existe. Lo primero es lo habitual en cuanto hay hermanos: el segundo hijo
     * no da de alta a un padre nuevo.
     *
     * <p><b>No actualiza los datos de contacto de una ficha existente.</b> Si el
     * telefono que llega difiere del que hay guardado, no se sabe cual de los
     * dos esta al dia, y machacarlo en silencio desde el alta de otro atleta es
     * la peor de las dos opciones. Se modifica desde el tutor, no de rebote.
     */
    public Guardian resolveOrCreate(GuardianRequest request) {
        Club club = clubService.getById(TenantContext.require());

        return guardianRepository.findByClubAndDni(club, request.getDni())
                .orElseGet(() -> {
                    Guardian guardian = new Guardian();
                    guardian.setClub(club);
                    guardian.setFirstName(request.getFirstName());
                    guardian.setLastName(request.getLastName());
                    guardian.setDni(request.getDni());
                    guardian.setEmail(request.getEmail());
                    guardian.setPhone(request.getPhone());
                    return guardianRepository.save(guardian);
                });
    }

    /** Idempotente: repetir el vinculo no lo duplica ni revienta contra el indice unico. */
    public AthleteGuardian link(Athlete athlete, Guardian guardian, GuardianRelationship relationship) {
        return athleteGuardianRepository
                .findByAthleteId(athlete.getId()).stream()
                .filter(link -> link.getGuardian().getId().equals(guardian.getId()))
                .findFirst()
                .orElseGet(() -> {
                    AthleteGuardian link = new AthleteGuardian();
                    link.setAthlete(athlete);
                    link.setGuardian(guardian);
                    link.setRelationship(relationship);
                    return athleteGuardianRepository.save(link);
                });
    }

    @Transactional(readOnly = true)
    public List<AthleteGuardian> findByAthlete(UUID athleteId) {
        return athleteGuardianRepository.findByAthleteId(athleteId);
    }
}
