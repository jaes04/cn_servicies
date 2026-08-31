package es.jaes.cn_servicies.config;

import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.club.ClubService;
import es.jaes.cn_servicies.user.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ClubService clubService;

    @Value("${ADMIN_USERNAME:}")
    private String adminUsername;

    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (adminUsername.isBlank() || adminPassword.isBlank()) {
            return;
        }

        // TODO (tarea 0.8): esta cuenta pasara a crearse junto con el club, en
        // ClubService.create(). Mientras tanto cuelga del club por defecto y la
        // existencia se comprueba dentro de ese club, porque el username es
        // unico por club y no global.
        Club club = clubService.getDefaultClub();

        if (userRepository.existsByClubAndUsername(club, adminUsername)) {
            log.info("Admin user '{}' already exists — skipping creation.", adminUsername);
            return;
        }

        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN not found in database"));

        User admin = new User();
        admin.setClub(club);
        admin.setUsername(adminUsername);
        admin.setEmail(adminUsername + "@admin.local");
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setBlocked(false);
        admin.setRoles(Set.of(adminRole));

        userRepository.save(admin);
        log.info("Admin user '{}' created successfully.", adminUsername);
    }
}