package es.jaes.cn_servicies.training_group;

import es.jaes.cn_servicies.season.SeasonRequest;
import es.jaes.cn_servicies.season.SeasonResponse;
import es.jaes.cn_servicies.season.SeasonService;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tarea 1.2: grupos de entrenamiento.
 *
 * <p>Lo que hay que demostrar es la duplicacion de una temporada a la
 * siguiente, que es la parte con reglas: que copia lo que debe, que <b>no</b>
 * copia la composicion, y que no se puede lanzar dos veces sobre la misma
 * temporada.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrainingGroupServiceTest {

    private static final UUID CLUB = UUID.fromString("33333333-0000-0000-0000-000000000033");
    private static final String SLUG = "club-grupos-it";
    private static final String ENTRENADOR = "coach_grupos_it";

    @Autowired private TrainingGroupService groupService;
    @Autowired private SeasonService seasonService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private UUID entrenador;
    private UUID temporadaVieja;
    private UUID temporadaNueva;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Grupos IT', ?, true, now())", CLUB, SLUG);
        entrenador = crearEntrenador();
    }

    @BeforeEach
    void contexto() {
        modoPublico();
        jdbc.update("DELETE FROM training_groups WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM seasons WHERE club_id = ?", CLUB);
        TenantContext.set(CLUB);

        temporadaVieja = crearTemporada("2024/2025", 2024);
        temporadaNueva = crearTemporada("2025/2026", 2025);
    }

    @AfterAll
    void fin() {
        TenantContext.clear();
        modoPublico();
        borrarTodo();
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void borrarTodo() {
        jdbc.update("DELETE FROM training_groups WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM seasons WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN"
                + " (SELECT id FROM users WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM users WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM clubs WHERE id = ?", CLUB);
    }

    private UUID crearEntrenador() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users"
                        + " (id, club_id, username, email, password_hash, blocked, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, false, now(), now())",
                id, CLUB, ENTRENADOR, ENTRENADOR + "@it.local",
                passwordEncoder.encode("clave-de-prueba-it"));
        jdbc.update("INSERT INTO user_roles (user_id, role_id)"
                + " SELECT ?, id FROM roles WHERE name = 'ROLE_TECHNICAL_STAFF'", id);
        return id;
    }

    private UUID crearTemporada(String nombre, int anio) {
        SeasonRequest request = new SeasonRequest();
        request.setName(nombre);
        request.setStartDate(LocalDate.of(anio, 9, 1));
        request.setEndDate(LocalDate.of(anio + 1, 8, 31));
        return seasonService.create(request).getId();
    }

    private TrainingGroupResponse crearGrupo(UUID temporada, String nombre, UUID coachId) {
        TrainingGroupRequest request = new TrainingGroupRequest();
        request.setSeasonId(temporada);
        request.setName(nombre);
        request.setCategory(GroupCategory.ALEVIN);
        request.setLevel(GroupLevel.COMPETICION);
        request.setMaxSlots(12);
        request.setCoachId(coachId);
        return groupService.create(request);
    }

    private List<TrainingGroupResponse> duplicar(UUID origen, UUID destino) {
        DuplicateGroupsRequest request = new DuplicateGroupsRequest();
        request.setFromSeasonId(origen);
        request.setToSeasonId(destino);
        return groupService.duplicate(request);
    }

    // ----------------------------------------------------------------
    //  1. Alta y unicidad
    // ----------------------------------------------------------------

    @Test
    @DisplayName("un grupo puede existir sin entrenador asignado")
    void sinEntrenadorSePuede() {
        TrainingGroupResponse grupo = crearGrupo(temporadaVieja, "Alevín A", null);

        assertThat(grupo.getCoachId()).isNull();
        assertThat(grupo.getCoachUsername()).isNull();
    }

    @Test
    @DisplayName("dos grupos con el mismo nombre en la misma temporada se rechazan")
    void nombreRepetidoEnLaMismaTemporada() {
        crearGrupo(temporadaVieja, "Alevín A", null);

        assertThatThrownBy(() -> crearGrupo(temporadaVieja, "Alevín A", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el mismo nombre en dos temporadas distintas es lo normal, no un error")
    void mismoNombreEnDistintaTemporada() {
        crearGrupo(temporadaVieja, "Alevín A", null);
        crearGrupo(temporadaNueva, "Alevín A", null);

        assertThat(groupService.findAll(null)).hasSize(2);
    }

    @Test
    @DisplayName("el listado se acota por temporada")
    void listadoPorTemporada() {
        crearGrupo(temporadaVieja, "Alevín A", null);
        crearGrupo(temporadaVieja, "Infantil B", null);
        crearGrupo(temporadaNueva, "Alevín A", null);

        assertThat(groupService.findAll(temporadaVieja)).hasSize(2);
        assertThat(groupService.findAll(temporadaNueva)).hasSize(1);
    }

    @Test
    @DisplayName("borrar un grupo lo saca del listado pero no borra la fila")
    void borradoLogico() {
        TrainingGroupResponse grupo = crearGrupo(temporadaVieja, "Alevín A", null);

        groupService.softDelete(grupo.getId());

        assertThat(groupService.findAll(temporadaVieja)).isEmpty();
        modoPublico();
        Integer filas = jdbc.queryForObject(
                "SELECT count(*) FROM training_groups WHERE id = ?", Integer.class, grupo.getId());
        TenantContext.set(CLUB);
        assertThat(filas).as("la fila sigue ahí, con deleted_at puesto").isEqualTo(1);
    }

    // ----------------------------------------------------------------
    //  2. Duplicar de una temporada a la siguiente
    // ----------------------------------------------------------------

    @Test
    @DisplayName("duplicar copia nombre, categoría, nivel, plazas y entrenador")
    void duplicarCopiaLosDatos() {
        crearGrupo(temporadaVieja, "Alevín A", entrenador);
        crearGrupo(temporadaVieja, "Infantil B", null);

        List<TrainingGroupResponse> copias = duplicar(temporadaVieja, temporadaNueva);

        assertThat(copias).hasSize(2);
        assertThat(groupService.findAll(temporadaNueva))
                .extracting(TrainingGroupResponse::getName)
                .containsExactlyInAnyOrder("Alevín A", "Infantil B");
        assertThat(copias)
                .filteredOn(g -> g.getName().equals("Alevín A"))
                .singleElement()
                .satisfies(g -> {
                    assertThat(g.getCoachId()).isEqualTo(entrenador);
                    assertThat(g.getMaxSlots()).isEqualTo(12);
                    assertThat(g.getCategory()).isEqualTo(GroupCategory.ALEVIN);
                    assertThat(g.getSeasonId()).isEqualTo(temporadaNueva);
                });
    }

    @Test
    @DisplayName("los originales se quedan donde estaban: duplicar copia, no mueve")
    void duplicarNoMueve() {
        crearGrupo(temporadaVieja, "Alevín A", null);

        duplicar(temporadaVieja, temporadaNueva);

        assertThat(groupService.findAll(temporadaVieja)).hasSize(1);
    }

    @Test
    @DisplayName("duplicar dos veces sobre la misma temporada se rechaza: es la protección contra el doble clic")
    void duplicarDosVecesSeRechaza() {
        crearGrupo(temporadaVieja, "Alevín A", null);
        duplicar(temporadaVieja, temporadaNueva);

        assertThatThrownBy(() -> duplicar(temporadaVieja, temporadaNueva))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(groupService.findAll(temporadaNueva))
                .as("catorce grupos no pueden convertirse en veintiocho")
                .hasSize(1);
    }

    @Test
    @DisplayName("duplicar una temporada sobre sí misma se rechaza")
    void duplicarSobreSiMisma() {
        crearGrupo(temporadaVieja, "Alevín A", null);

        assertThatThrownBy(() -> duplicar(temporadaVieja, temporadaVieja))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("duplicar desde una temporada sin grupos avisa en vez de no hacer nada")
    void duplicarDesdeVacia() {
        assertThatThrownBy(() -> duplicar(temporadaVieja, temporadaNueva))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
