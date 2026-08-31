package es.jaes.cn_servicies.club;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Superficie publica del modulo club. Los demas modulos pasan por aqui, nunca
 * por {@link ClubRepository}.
 */
@Service
@RequiredArgsConstructor
public class ClubService {

    private final ClubRepository clubRepository;

    @Value("${app.default-club-slug:sierra-oeste}")
    private String defaultClubSlug;

    /**
     * Club al que se asigna todo lo que se crea mientras no exista contexto de
     * tenant.
     *
     * <p><b>Temporal.</b> Desaparece en la tarea 0.4: a partir de ahi el club
     * sale del {@code TenantContext}, que a su vez lo toma del claim del JWT.
     * Mientras tanto la aplicacion sigue siendo mono-club y esta es la unica
     * forma de satisfacer el {@code NOT NULL} de {@code club_id} sin inventar
     * un club por peticion.
     */
    public Club getDefaultClub() {
        return clubRepository.findBySlug(defaultClubSlug)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el club por defecto con slug '" + defaultClubSlug + "'"));
    }

    public Club getById(UUID id) {
        return clubRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Club no encontrado"));
    }
}
