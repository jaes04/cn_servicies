package es.jaes.cn_servicies.club;

import es.jaes.cn_servicies.tenant.TenantContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tarea 0.8: el alta de un club crea a su administrador, y nunca queda un club
 * sin nadie que pueda entrar.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClubCreationTest {

    private static final String SLUG = "club-alta-it";
    private static final String NOMBRE = "Club Alta IT";
    private static final String ADMIN = "admin_alta_it";
    private static final String CORREO = "admin_alta_it@it.local";
    private static final String CLAVE = "clave-de-prueba-it";

    @Autowired private ClubService clubService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeAll
    void inicio() {
        limpiar();
    }

    @BeforeEach
    void contexto() {
        // El alta de un club ocurre fuera de cualquier peticion, asi que no hay
        // club en contexto. Es justo el caso que tiene que funcionar.
        TenantContext.clear();
        modoPublico();
    }

    @AfterAll
    void fin() {
        limpiar();
        TenantContext.clear();
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void limpiar() {
        modoPublico();
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT u.id FROM users u"
                + " JOIN clubs c ON c.id = u.club_id WHERE c.slug = ?)", SLUG);
        jdbc.update("DELETE FROM users WHERE club_id IN (SELECT id FROM clubs WHERE slug = ?)", SLUG);
        jdbc.update("DELETE FROM users WHERE username = ?", ADMIN);
        jdbc.update("DELETE FROM clubs WHERE slug = ?", SLUG);
    }

    private int clubesConEseSlug() {
        return jdbc.queryForObject("SELECT count(*) FROM clubs WHERE slug = ?", Integer.class, SLUG);
    }

    @Test
    @DisplayName("el alta crea el club y su administrador con ROLE_ADMIN")
    void creaClubYAdministrador() {
        Club club = clubService.create(NOMBRE, SLUG, ADMIN, CORREO, CLAVE);

        assertThat(club.getId()).isNotNull();
        assertThat(club.isActive()).isTrue();

        modoPublico();
        String rol = jdbc.queryForObject(
                "SELECT r.name FROM users u"
                        + " JOIN user_roles ur ON ur.user_id = u.id"
                        + " JOIN roles r ON r.id = ur.role_id"
                        + " WHERE u.username = ? AND u.club_id = ?",
                String.class, ADMIN, club.getId());

        assertThat(rol).isEqualTo("ROLE_ADMIN");

        limpiar();
    }

    @Test
    @DisplayName("si falla el alta del administrador, no se crea el club")
    void siFallaElAdminNoQuedaClub() {
        // Un correo ya en uso hace fallar el alta del usuario: el email sigue
        // siendo unico global. Sirve para provocar el fallo a mitad.
        UUID otroClub = UUID.randomUUID();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Ocupa correo', 'club-ocupa-correo-it', true, now())", otroClub);
        jdbc.update("INSERT INTO users"
                + " (id, club_id, username, email, password_hash, blocked, created_at, updated_at)"
                + " VALUES (?, ?, 'ocupa_correo_it', ?, 'x', false, now(), now())",
                UUID.randomUUID(), otroClub, CORREO);

        try {
            assertThatThrownBy(() -> clubService.create(NOMBRE, SLUG, ADMIN, CORREO, CLAVE))
                    .isInstanceOf(IllegalArgumentException.class);

            modoPublico();
            assertThat(clubesConEseSlug())
                    .as("el club no puede quedar creado si su administrador no lo esta")
                    .isZero();
        } finally {
            modoPublico();
            jdbc.update("DELETE FROM users WHERE club_id = ?", otroClub);
            jdbc.update("DELETE FROM clubs WHERE id = ?", otroClub);
            limpiar();
        }
    }

    @Test
    @DisplayName("no se puede dar de alta dos clubes con el mismo slug")
    void noPermiteSlugRepetido() {
        clubService.create(NOMBRE, SLUG, ADMIN, CORREO, CLAVE);

        assertThatThrownBy(() -> clubService.create(NOMBRE, SLUG, "otro_admin_it",
                "otro_admin_it@it.local", CLAVE))
                .isInstanceOf(IllegalArgumentException.class);

        limpiar();
    }
}
