package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.tenant.TenantContext;
import es.jaes.cn_servicies.training_group.AthleteGroupService;
import es.jaes.cn_servicies.training_group.LeaveReason;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Tarea 2.4: informes de asistencia.
 *
 * <p>Todo el bloque se reduce a una pregunta —<b>que se divide entre que</b>— y
 * a que la respuesta no mienta en los tres casos que la ensucian: las sesiones
 * canceladas, las de antes de que el atleta entrara al grupo, y las que se
 * celebraron sin que nadie le marcara nada.
 *
 * <p>Las sesiones se insertan a pelo con estados y fechas fijas: aqui lo que se
 * prueba es la aritmetica, no el generador.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttendanceReportServiceTest {

    private static final UUID CLUB = UUID.fromString("eeee5555-0000-0000-0000-0000000055ee");
    private static final String SLUG = "club-informes-it";

    private static final LocalDate INICIO = LocalDate.now().minusMonths(2);
    private static final LocalDate FIN = LocalDate.now().plusMonths(2);

    /** Cuatro martes seguidos, todos ya pasados. */
    private static final LocalDate S1 = LocalDate.now().minusDays(28);
    private static final LocalDate S2 = LocalDate.now().minusDays(21);
    private static final LocalDate S3 = LocalDate.now().minusDays(14);
    private static final LocalDate S4 = LocalDate.now().minusDays(7);

    @Autowired private AttendanceReportService reportService;
    @Autowired private AthleteGroupService membershipService;
    @Autowired private JdbcTemplate jdbc;

    private UUID grupo;
    private UUID ana;
    private UUID bruno;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Informes IT', ?, true, now())", CLUB, SLUG);
    }

    @BeforeEach
    void contexto() {
        modoPublico();
        vaciar();
        grupo = montarGrupo();
        ana = crearAtleta("Ana", "30000001A");
        bruno = crearAtleta("Bruno", "30000002B");
        TenantContext.set(CLUB);
        membershipService.assign(ana, grupo, INICIO, null);
        membershipService.assign(bruno, grupo, INICIO, null);
    }

    @AfterAll
    void fin() {
        TenantContext.clear();
        modoPublico();
        borrarTodo();
    }

    // ----------------------------------------------------------------
    //  1. Qué entra en el denominador
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el porcentaje sale sobre las sesiones celebradas")
    void porcentajeBasico() {
        UUID s1 = sesionCelebrada(S1);
        UUID s2 = sesionCelebrada(S2);
        marcar(s1, ana, "PRESENT");
        marcar(s2, ana, "ABSENT");
        marcar(s1, bruno, "LATE");
        marcar(s2, bruno, "PRESENT");

        AthleteAttendanceResponse informe = reportService.forAthlete(ana, INICIO, FIN);

        assertThat(informe.getSessions()).isEqualTo(2);
        assertThat(informe.getPresent()).isEqualTo(1);
        assertThat(informe.getAbsent()).isEqualTo(1);
        assertThat(informe.getAttendanceRate()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("llegar tarde cuenta como asistencia")
    void tardeCuenta() {
        marcar(sesionCelebrada(S1), ana, "LATE");

        assertThat(reportService.forAthlete(ana, INICIO, FIN).getAttendanceRate())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("una sesión cancelada no cuenta: faltar a lo que no existió no es faltar")
    void laCanceladaNoCuenta() {
        marcar(sesionCelebrada(S1), ana, "PRESENT");
        sesionConEstado(S2, "CANCELLED");

        assertThat(reportService.forAthlete(ana, INICIO, FIN).getSessions())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("una sesión futura tampoco cuenta")
    void laFuturaNoCuenta() {
        marcar(sesionCelebrada(S1), ana, "PRESENT");
        sesionConEstado(LocalDate.now().plusDays(7), "SCHEDULED");

        assertThat(reportService.forAthlete(ana, INICIO, FIN).getSessions())
                .isEqualTo(1);
    }

    /** Quien se incorporó en enero no puede arrastrar las faltas de octubre. */
    @Test
    @DisplayName("no cuentan las sesiones anteriores a la entrada del atleta al grupo")
    void soloDesdeQueEsMiembro() {
        UUID tarde = crearAtleta("Carla", "30000003C");
        TenantContext.set(CLUB);
        membershipService.assign(tarde, grupo, S3, null);

        sesionCelebrada(S1);
        sesionCelebrada(S2);
        marcar(sesionCelebrada(S3), tarde, "PRESENT");
        marcar(sesionCelebrada(S4), tarde, "PRESENT");

        AthleteAttendanceResponse informe = reportService.forAthlete(tarde, INICIO, FIN);

        assertThat(informe.getSessions()).as("solo las dos desde que entró").isEqualTo(2);
        assertThat(informe.getAttendanceRate()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("tampoco cuentan las posteriores a su baja")
    void hastaQueSeVa() {
        marcar(sesionCelebrada(S1), bruno, "PRESENT");
        marcar(sesionCelebrada(S2), bruno, "PRESENT");
        sesionCelebrada(S3);
        sesionCelebrada(S4);
        membershipService.leave(bruno, grupo, S2, LeaveReason.GROUP_CHANGE);

        assertThat(reportService.forAthlete(bruno, INICIO, FIN).getSessions())
                .as("leftOn es el último día incluido")
                .isEqualTo(2);
    }

    // ----------------------------------------------------------------
    //  2. Lo que no se registró
    // ----------------------------------------------------------------

    /**
     * La decisión del bloque: cuenta como falta, pero sale con su fecha para
     * poder ir a corregirla.
     */
    @Test
    @DisplayName("una sesión celebrada sin marcar cuenta como falta y sale como incidencia")
    void sinRegistrarCuentaYSeAvisa() {
        marcar(sesionCelebrada(S1), ana, "PRESENT");
        UUID s2 = sesionCelebrada(S2);
        marcar(s2, bruno, "PRESENT");   // se pasó lista, pero a Ana se le olvidó

        AthleteAttendanceResponse informe = reportService.forAthlete(ana, INICIO, FIN);

        assertThat(informe.getSessions()).isEqualTo(2);
        assertThat(informe.getUnrecorded()).isEqualTo(1);
        assertThat(informe.getAttendanceRate())
                .as("una de dos: la no registrada cuenta como falta")
                .isEqualTo(0.5);
        assertThat(informe.getIncidents())
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.getDate()).isEqualTo(S2);
                    assertThat(i.getSessionId()).isEqualTo(s2);
                });
    }

    /**
     * Contar catorce ausencias porque el entrenador no abrió el móvil sería
     * ruido en el historial de catorce familias.
     */
    @Test
    @DisplayName("una sesión a la que nadie pasó lista no cuenta como falta de nadie")
    void sesionSinListaNoPenaliza() {
        marcar(sesionCelebrada(S1), ana, "PRESENT");
        UUID sinLista = sesionConEstado(S2, "SCHEDULED");

        AthleteAttendanceResponse informe = reportService.forAthlete(ana, INICIO, FIN);
        assertThat(informe.getSessions()).as("solo la celebrada").isEqualTo(1);
        assertThat(informe.getAttendanceRate()).isEqualTo(1.0);

        assertThat(reportService.forGroup(grupo, INICIO, FIN).getSessionsWithoutRoster())
                .as("pero el club tiene que enterarse de que falta por registrar")
                .singleElement()
                .satisfies(i -> assertThat(i.getSessionId()).isEqualTo(sinLista));
    }

    // ----------------------------------------------------------------
    //  3. Por grupo
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el informe de grupo trae a cada atleta y la media del grupo")
    void informeDeGrupo() {
        UUID s1 = sesionCelebrada(S1);
        UUID s2 = sesionCelebrada(S2);
        marcar(s1, ana, "PRESENT");
        marcar(s2, ana, "PRESENT");
        marcar(s1, bruno, "PRESENT");
        marcar(s2, bruno, "ABSENT");

        GroupAttendanceResponse informe = reportService.forGroup(grupo, INICIO, FIN);

        assertThat(informe.getSessions()).isEqualTo(2);
        assertThat(informe.getAthletes()).hasSize(2);
        assertThat(informe.getAttendanceRate())
                .as("tres asistencias de cuatro posibles")
                .isEqualTo(0.75, within(0.0001));
    }

    /**
     * Promediar los porcentajes daría 75%: quien solo pudo ir a una sesión
     * pesaría lo mismo que quien pudo ir a tres.
     */
    @Test
    @DisplayName("la media del grupo pondera por sesiones posibles, no promedia porcentajes")
    void mediaPonderada() {
        UUID tarde = crearAtleta("Carla", "30000004D");
        TenantContext.set(CLUB);
        membershipService.assign(tarde, grupo, S4, null);

        UUID s1 = sesionCelebrada(S1);
        UUID s2 = sesionCelebrada(S2);
        UUID s4 = sesionCelebrada(S4);
        marcar(s1, ana, "PRESENT");
        marcar(s2, ana, "PRESENT");
        marcar(s4, ana, "PRESENT");
        marcar(s1, bruno, "ABSENT");
        marcar(s2, bruno, "ABSENT");
        marcar(s4, bruno, "ABSENT");
        marcar(s4, tarde, "ABSENT");

        // Ana 3/3, Bruno 0/3, Carla 0/1 → 3 de 7, no la media de (1 + 0 + 0).
        assertThat(reportService.forGroup(grupo, INICIO, FIN).getAttendanceRate())
                .isEqualTo(0.4286, within(0.0001));
    }

    // ----------------------------------------------------------------
    //  4. Ausencias consecutivas
    // ----------------------------------------------------------------

    @Test
    @DisplayName("tres faltas seguidas salen; dos no")
    void rachaDeFaltas() {
        UUID s1 = sesionCelebrada(S1);
        UUID s2 = sesionCelebrada(S2);
        UUID s3 = sesionCelebrada(S3);
        UUID s4 = sesionCelebrada(S4);
        // Ana: vino la primera y faltó las tres siguientes.
        marcar(s1, ana, "PRESENT");
        marcar(s2, ana, "ABSENT");
        marcar(s3, ana, "ABSENT");
        marcar(s4, ana, "ABSENT");
        // Bruno: solo las dos últimas.
        marcar(s1, bruno, "PRESENT");
        marcar(s2, bruno, "PRESENT");
        marcar(s3, bruno, "ABSENT");
        marcar(s4, bruno, "ABSENT");

        List<AttendanceGapResponse> gaps = reportService.gaps(grupo, INICIO, FIN, null);

        assertThat(gaps).singleElement().satisfies(g -> {
            assertThat(g.getAthleteName()).isEqualTo("Ana Nadadora");
            assertThat(g.getConsecutiveAbsences()).isEqualTo(3);
            assertThat(g.getLastAttendedOn()).isEqualTo(S1);
        });
    }

    @Test
    @DisplayName("volver rompe la racha")
    void volverRompeLaRacha() {
        UUID s1 = sesionCelebrada(S1);
        UUID s2 = sesionCelebrada(S2);
        UUID s3 = sesionCelebrada(S3);
        UUID s4 = sesionCelebrada(S4);
        marcar(s1, ana, "ABSENT");
        marcar(s2, ana, "ABSENT");
        marcar(s3, ana, "ABSENT");
        marcar(s4, ana, "PRESENT");
        // A Bruno también, o sus cuatro sesiones sin registrar lo sacarían como
        // racha y este test dejaría de hablar de lo que quiere hablar.
        marcar(s1, bruno, "PRESENT");
        marcar(s2, bruno, "PRESENT");
        marcar(s3, bruno, "PRESENT");
        marcar(s4, bruno, "PRESENT");

        assertThat(reportService.gaps(grupo, INICIO, FIN, null))
                .as("las tres faltas ya no son la racha actual")
                .isEmpty();
    }

    @Test
    @DisplayName("las no registradas rompen la racha igual que una falta")
    void lasNoRegistradasCuentanEnLaRacha() {
        sesionCelebrada(S1);
        sesionCelebrada(S2);
        // Se pasa lista solo de Bruno: Ana queda sin registrar en las tres.
        UUID s3 = sesionCelebrada(S3);
        marcar(s3, bruno, "PRESENT");

        assertThat(reportService.gaps(grupo, INICIO, FIN, null))
                .as("si no se apunta a alguien, el sistema no puede decir que viene")
                .extracting(AttendanceGapResponse::getAthleteName)
                .contains("Ana Nadadora");
    }

    @Test
    @DisplayName("el umbral se puede ajustar")
    void umbralAjustable() {
        UUID s1 = sesionCelebrada(S1);
        UUID s2 = sesionCelebrada(S2);
        marcar(s1, ana, "ABSENT");
        marcar(s2, ana, "ABSENT");
        marcar(s1, bruno, "PRESENT");
        marcar(s2, bruno, "PRESENT");

        assertThat(reportService.gaps(grupo, INICIO, FIN, 2)).hasSize(1);
        assertThat(reportService.gaps(grupo, INICIO, FIN, 3)).isEmpty();
    }

    @Test
    @DisplayName("un umbral de cero se rechaza: todo el mundo sería una alerta")
    void umbralCero() {
        assertThatThrownBy(() -> reportService.gaps(grupo, INICIO, FIN, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un rango invertido se rechaza")
    void rangoInvertido() {
        assertThatThrownBy(() -> reportService.forGroup(grupo, FIN, INICIO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------
    //  Andamiaje
    // ----------------------------------------------------------------

    private UUID sesionCelebrada(LocalDate fecha) {
        return sesionConEstado(fecha, "DONE");
    }

    private UUID sesionConEstado(LocalDate fecha, String estado) {
        UUID id = UUID.randomUUID();
        modoPublico();
        jdbc.update("INSERT INTO training_sessions"
                        + " (id, club_id, group_id, schedule_id, session_date, start_time, end_time,"
                        + "  modality, status, created_at, updated_at)"
                        + " VALUES (?, ?, ?, NULL, ?, '18:00', '19:00', 'SWIMMING', ?, now(), now())",
                id, CLUB, grupo, fecha, estado);
        TenantContext.set(CLUB);
        return id;
    }

    private void marcar(UUID sesion, UUID atleta, String estado) {
        modoPublico();
        jdbc.update("INSERT INTO attendance"
                        + " (id, session_id, athlete_id, status, registered_by_id, registered_at)"
                        + " VALUES (?, ?, ?, ?, NULL, now())",
                UUID.randomUUID(), sesion, atleta, estado);
        TenantContext.set(CLUB);
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void vaciar() {
        modoPublico();
        jdbc.update("DELETE FROM attendance WHERE session_id IN"
                + " (SELECT id FROM training_sessions WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM training_sessions WHERE club_id = ?", CLUB);
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

    private UUID montarGrupo() {
        UUID temporada = UUID.randomUUID();
        UUID grupoId = UUID.randomUUID();
        jdbc.update("INSERT INTO seasons"
                        + " (id, club_id, name, start_date, end_date, active, created_at, updated_at)"
                        + " VALUES (?, ?, 'Temporada informes IT', ?, ?, true, now(), now())",
                temporada, CLUB, INICIO, FIN);
        jdbc.update("INSERT INTO training_groups"
                        + " (id, club_id, season_id, name, category, level, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'Alevín A', 'ALEVIN', 'COMPETICION', now(), now())",
                grupoId, CLUB, temporada);
        return grupoId;
    }

    private UUID crearAtleta(String nombre, String dni) {
        UUID id = UUID.randomUUID();
        modoPublico();
        jdbc.update("INSERT INTO athletes"
                        + " (id, club_id, first_name, last_name, birth_date, dni, gender_id,"
                        + "  created_at, updated_at)"
                        + " SELECT ?, ?, ?, 'Nadadora', DATE '2013-01-01', ?, g.id, now(), now()"
                        + " FROM genders g WHERE g.name = 'FEMALE'",
                id, CLUB, nombre, dni);
        return id;
    }
}
