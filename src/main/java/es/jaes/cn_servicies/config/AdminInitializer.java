package es.jaes.cn_servicies.config;

import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.club.ClubService;
import es.jaes.cn_servicies.season.SeasonService;
import es.jaes.cn_servicies.user.RoleName;
import es.jaes.cn_servicies.user.UserRequest;
import es.jaes.cn_servicies.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Deja la aplicacion utilizable en el primer arranque: un club y alguien que
 * pueda entrar en el.
 *
 * <p>Si no hay club por defecto, lo crea junto con su administrador en una sola
 * transaccion (tarea 0.8). Si ya lo hay, se limita a asegurar que ese
 * administrador existe. En ambos casos siembra la temporada en curso si el club
 * no tiene ninguna (tarea 1.1).
 *
 * <p>{@code @Transactional} no esta por la atomicidad de una sola fila: esta
 * para que el aspecto de tenancy entre y fije {@code app.club_id}. Sin esa
 * variable, las policies de Row Level Security rechazan la insercion.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Transactional
public class AdminInitializer implements CommandLineRunner {

    private final ClubService clubService;
    private final UserService userService;
    private final SeasonService seasonService;

    @Value("${ADMIN_USERNAME:}")
    private String adminUsername;

    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (adminUsername.isBlank() || adminPassword.isBlank()) {
            return;
        }

        Club club = clubService.findDefault()
                .map(this::asegurarAdministrador)
                .orElseGet(this::crearClubPorDefecto);

        // Al final y para los dos caminos: un club recien creado la necesita, y
        // uno que ya existia de antes de la 1.1 tampoco la tiene. Es idempotente
        // y no hace nada si el club ya tiene alguna.
        seasonService.ensureCurrentSeason(club);
    }

    private Club crearClubPorDefecto() {
        Club club = clubService.createDefault(adminUsername, correoDelAdmin(), adminPassword);
        log.info("Club por defecto creado junto con su administrador '{}'.", adminUsername);
        return club;
    }

    private Club asegurarAdministrador(Club club) {
        // Acotado al club: el username es unico por club, no global, asi que
        // preguntar solo por el nombre no distingue nada.
        if (userService.existsInClub(club, adminUsername)) {
            log.info("Admin user '{}' already exists — skipping creation.", adminUsername);
            return club;
        }

        UserRequest admin = new UserRequest();
        admin.setUsername(adminUsername);
        admin.setEmail(correoDelAdmin());
        admin.setPassword(adminPassword);
        admin.setRoles(Set.of(RoleName.ROLE_ADMIN));
        userService.create(admin, club);

        log.info("Admin user '{}' created successfully.", adminUsername);
        return club;
    }

    private String correoDelAdmin() {
        return adminUsername + "@admin.local";
    }
}
