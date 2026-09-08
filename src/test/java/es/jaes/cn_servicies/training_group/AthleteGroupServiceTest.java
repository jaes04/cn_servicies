package es.jaes.cn_servicies.training_group;

import es.jaes.cn_servicies.tenant.TenantContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tarea 1.3: pertenencia historica.
 *
 * <p>El nucleo de esta clase es <b>"miembros a una fecha dada"</b> y sus dos
 * extremos. Sobre esa consulta se apoya toda la Fase 2: si se equivoca en un
 * dia, se equivoca en cada lista de asistencia que se genere a partir de ella,
 * y el fallo no se ve hasta que alguien reclama una falta de hace tres meses.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AthleteGroupServiceTest {

    private static final UUID CLUB = UUID.fromString("55555555-0000-0000-0000-000000000055");
    private static final String SLUG = "club-pertenencia-it";

    private static final LocalDate INICIO_TEMPORADA = LocalDate.of(2024, 9, 1);
    private static final LocalDate FIN_TEMPORADA = LocalDate.of(2025, 8, 31);
    private static final LocalDate ULTIMO_DIA = LocalDate.of(2025, 1, 31);

    @Autowired private AthleteGroupService membershipService;
    @Autowired private JdbcTemplate jdbc;

    private UUID temporada;
    private UUID natacion;
    private UUID preparacionFisica;
    private UUID nadador;
    private UUID otroNadador;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Pertenencia IT', ?, true, now())", CLUB, SLUG);
    }

    @BeforeEach
    void contexto() {
        modoPublico();
        vaciar();

        temporada = crearTemporada();
        natacion = crearGrupo("Alevín A");
        preparacionFisica = crearGrupo("Preparación física");
        nadador = crearAtleta("99999991A");
        otroNadador = crearAtleta("99999992B");

        TenantContext.set(CLUB);
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

    private void vaciar() {
        jdbc.update("DELETE FROM athlete_groups WHERE athlete_id IN"
                + " (SELECT id FROM athletes WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM training_groups WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM athletes WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM seasons WHERE club_id = ?", CLUB);
    }

    private void borrarTodo() {
        vaciar();
        jdbc.update("DELETE FROM clubs WHERE id = ?", CLUB);
    }

    private UUID crearTemporada() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO seasons"
                        + " (id, club_id, name, start_date, end_date, active, created_at, updated_at)"
                        + " VALUES (?, ?, '2024/2025', ?, ?, true, now(), now())",
                id, CLUB, INICIO_TEMPORADA, FIN_TEMPORADA);
        return id;
    }

    private UUID crearGrupo(String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO training_groups"
                        + " (id, club_id, season_id, name, category, level, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, 'ALEVIN', 'COMPETICION', now(), now())",
                id, CLUB, temporada, nombre);
        return id;
    }

    private UUID crearAtleta(String dni) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO athletes"
                        + " (id, club_id, first_name, last_name, birth_date, dni, gender_id, created_at, updated_at)"
                        + " SELECT ?, ?, 'Atleta', 'Pertenencia', DATE '2013-01-01', ?, g.id, now(), now()"
                        + " FROM genders g WHERE g.name = 'MALE'",
                id, CLUB, dni);
        return id;
    }

    // ----------------------------------------------------------------
    //  1. Miembros a una fecha dada — la pieza de la Fase 2
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el día de la baja el atleta TODAVÍA cuenta como miembro")
    void elDiaDeLaBajaTodaviaCuenta() {
        membershipService.assign(nadador, natacion, INICIO_TEMPORADA, null);
        membershipService.leave(nadador, natacion, ULTIMO_DIA, LeaveReason.LEFT_CLUB);

        assertThat(membershipService.membersOn(natacion, ULTIMO_DIA))
                .as("left_on es el último día de pertenencia, no el primero fuera")
                .hasSize(1);
    }

    @Test
    @DisplayName("el día siguiente a la baja ya no cuenta")
    void elDiaSiguienteYaNo() {
        membershipService.assign(nadador, natacion, INICIO_TEMPORADA, null);
        membershipService.leave(nadador, natacion, ULTIMO_DIA, LeaveReason.LEFT_CLUB);

        assertThat(membershipService.membersOn(natacion, ULTIMO_DIA.plusDays(1))).isEmpty();
    }

    @Test
    @DisplayName("el día del alta ya cuenta como miembro")
    void elDiaDelAltaYaCuenta() {
        membershipService.assign(nadador, natacion, INICIO_TEMPORADA, null);

        assertThat(membershipService.membersOn(natacion, INICIO_TEMPORADA)).hasSize(1);
    }

    @Test
    @DisplayName("la víspera del alta no cuenta")
    void lavisperaNoCuenta() {
        membershipService.assign(nadador, natacion, INICIO_TEMPORADA.plusDays(10), null);

        assertThat(membershipService.membersOn(natacion, INICIO_TEMPORADA.plusDays(9))).isEmpty();
    }

    @Test
    @DisplayName("la consulta devuelve quién estaba entonces, no quién está hoy")
    void devuelveQuienEstabaEntonces() {
        // Uno entra en septiembre y se va en enero; el otro entra en febrero.
        membershipService.assign(nadador, natacion, INICIO_TEMPORADA, null);
        membershipService.leave(nadador, natacion, ULTIMO_DIA, LeaveReason.GROUP_CHANGE);
        membershipService.assign(otroNadador, natacion, LocalDate.of(2025, 2, 1), null);

        assertThat(membershipService.membersOn(natacion, LocalDate.of(2024, 10, 15)))
                .as("en octubre solo estaba el primero")
                .hasSize(1)
                .allSatisfy(m -> assertThat(m.getAthlete().getId()).isEqualTo(nadador));

        assertThat(membershipService.membersOn(natacion, LocalDate.of(2025, 6, 1)))
                .as("en junio solo estaba el segundo")
                .hasSize(1)
                .allSatisfy(m -> assertThat(m.getAthlete().getId()).isEqualTo(otroNadador));
    }

    // ----------------------------------------------------------------
    //  2. Varios grupos a la vez, uno solo por grupo
    // ----------------------------------------------------------------

    @Test
    @DisplayName("un atleta puede estar en natación y en preparación física a la vez")
    void variosGruposALaVez() {
        membershipService.assign(nadador, natacion, INICIO_TEMPORADA, null);
        membershipService.assign(nadador, preparacionFisica, INICIO_TEMPORADA, null);

        assertThat(membershipService.openMembershipsOf(nadador)).hasSize(2);
    }

    @Test
    @DisplayName("pero no dos veces abierto en el mismo grupo")
    void noDosVecesEnElMismoGrupo() {
        membershipService.assign(nadador, natacion, INICIO_TEMPORADA, null);

        assertThatThrownBy(() ->
                membershipService.assign(nadador, natacion, INICIO_TEMPORADA.plusDays(30), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la base tampoco deja dos abiertas en el mismo grupo, aunque se llegue por fuera")
    void laBaseTampocoDejaDosAbiertas() {
        membershipService.assign(nadador, natacion, INICIO_TEMPORADA, null);

        modoPublico();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO athlete_groups"
                        + " (id, athlete_id, group_id, joined_on, created_at)"
                        + " VALUES (?, ?, ?, ?, now())",
                UUID.randomUUID(), nadador, natacion, INICIO_TEMPORADA))
                .isInstanceOf(DataIntegrityViolationException.class);
        TenantContext.set(CLUB);
    }

    @Test
    @DisplayName("volver a entrar en el mismo grupo tras una baja sí se permite: es histórico, no duplicado")
    void volverAEntrarSePermite() {
        membershipService.assign(nadador, natacion, INICIO_TEMPORADA, null);
        membershipService.leave(nadador, natacion, ULTIMO_DIA, LeaveReason.LEFT_CLUB);

        membershipService.assign(nadador, natacion, ULTIMO_DIA.plusMonths(1), null);

        assertThat(membershipService.historyOf(nadador, null)).hasSize(2);
        assertThat(membershipService.openMembershipsOf(nadador)).hasSize(1);
    }

    // ----------------------------------------------------------------
    //  3. Mover de grupo
    // ----------------------------------------------------------------

    @Test
    @DisplayName("mover de grupo cierra el anterior y deja abierto solo el nuevo")
    void moverDeGrupo() {
        membershipService.assign(nadador, natacion, INICIO_TEMPORADA, null);

        membershipService.assign(nadador, preparacionFisica, ULTIMO_DIA, natacion);

        assertThat(membershipService.openMembershipsOf(nadador))
                .hasSize(1)
                .allSatisfy(m -> assertThat(m.getTrainingGroup().getId()).isEqualTo(preparacionFisica));
        assertThat(membershipService.historyOf(nadador, null))
                .filteredOn(m -> m.getTrainingGroup().getId().equals(natacion))
                .singleElement()
                .satisfies(m -> {
                    assertThat(m.getLeftOn()).isEqualTo(ULTIMO_DIA);
                    assertThat(m.getLeaveReason()).isEqualTo(LeaveReason.GROUP_CHANGE);
                });
    }

    @Test
    @DisplayName("dar de alta sin indicar traslado NO cierra los otros grupos")
    void elAltaSuletaNoCierraNada() {
        membershipService.assign(nadador, natacion, INICIO_TEMPORADA, null);

        membershipService.assign(nadador, preparacionFisica, INICIO_TEMPORADA, null);

        assertThat(membershipService.openMembershipsOf(nadador))
                .as("adivinar el traslado daría de baja al nadador de preparación física")
                .hasSize(2);
    }

    // ----------------------------------------------------------------
    //  4. Baja
    // ----------------------------------------------------------------

    @Test
    @DisplayName("la baja no borra la fila")
    void laBajaNoBorra() {
        membershipService.assign(nadador, natacion, INICIO_TEMPORADA, null);

        membershipService.leave(nadador, natacion, ULTIMO_DIA, LeaveReason.END_OF_SEASON);

        modoPublico();
        Integer filas = jdbc.queryForObject(
                "SELECT count(*) FROM athlete_groups WHERE athlete_id = ? AND group_id = ?",
                Integer.class, nadador, natacion);
        TenantContext.set(CLUB);

        assertThat(filas).as("el histórico de quién estuvo en el grupo tiene que sobrevivir").isEqualTo(1);
    }

    @Test
    @DisplayName("una baja anterior al alta se rechaza")
    void bajaAnteriorAlAlta() {
        membershipService.assign(nadador, natacion, ULTIMO_DIA, null);

        assertThatThrownBy(() -> membershipService.leave(
                nadador, natacion, ULTIMO_DIA.minusDays(1), LeaveReason.OTHER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("dar de baja a quien no está en el grupo es un 404")
    void bajaDeQuienNoEsta() {
        assertThatThrownBy(() -> membershipService.leave(
                nadador, natacion, ULTIMO_DIA, LeaveReason.OTHER))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    // ----------------------------------------------------------------
    //  5. El alta cae dentro de la temporada
    // ----------------------------------------------------------------

    @Test
    @DisplayName("un alta fuera de la temporada del grupo se rechaza")
    void altaFueraDeTemporada() {
        assertThatThrownBy(() -> membershipService.assign(
                nadador, natacion, INICIO_TEMPORADA.minusDays(1), null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> membershipService.assign(
                nadador, natacion, FIN_TEMPORADA.plusDays(1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------
    //  6. Histórico por temporada
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el histórico se puede acotar a una temporada")
    void historicoPorTemporada() {
        membershipService.assign(nadador, natacion, INICIO_TEMPORADA, null);
        membershipService.assign(nadador, preparacionFisica, INICIO_TEMPORADA, null);

        assertThat(membershipService.historyOf(nadador, temporada)).hasSize(2);
        assertThat(membershipService.historyOf(nadador, UUID.randomUUID()))
                .as("otra temporada no devuelve nada")
                .isEmpty();
    }

    @Test
    @DisplayName("el conteo de miembros actuales solo cuenta las pertenencias abiertas")
    void conteoDeMiembrosActuales() {
        membershipService.assign(nadador, natacion, INICIO_TEMPORADA, null);
        membershipService.assign(otroNadador, natacion, INICIO_TEMPORADA, null);
        membershipService.leave(nadador, natacion, ULTIMO_DIA, LeaveReason.LEFT_CLUB);

        assertThat(membershipService.currentMemberCount(natacion)).isEqualTo(1);
    }
}
