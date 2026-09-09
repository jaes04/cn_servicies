package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.club.ClubService;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 2.2.b: el job que mantiene generado el calendario.
 *
 * <p>Lo que hay que demostrar es que <b>funciona sin peticion HTTP</b>. El job
 * corre de madrugada, sin JWT y sin nadie que haya puesto el
 * {@code TenantContext}: lo fija el propio bucle, club a club, y si se le
 * olvidara limpiarlo el siguiente club heredaria el anterior. Eso, en un
 * multi-tenant, es la peor clase de fallo.
 *
 * <p>Se llama al metodo directamente en vez de esperar al cron: un test que
 * dependiera del reloj tardaria horas y no probaria nada mas.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SessionGenerationJobTest {

    private static final UUID CLUB_A = UUID.fromString("bbbb2222-0000-0000-0000-00000000aa11");
    private static final UUID CLUB_B = UUID.fromString("bbbb2222-0000-0000-0000-00000000bb22");
    /** Sin temporada activa: el job tiene que pasar de largo sin romperse. */
    private static final UUID CLUB_VACIO = UUID.fromString("bbbb2222-0000-0000-0000-00000000cc33");

    private static final LocalDate HOY = LocalDate.now();
    private static final LocalDate INICIO = HOY.minusMonths(3);
    private static final LocalDate FIN = HOY.plusMonths(8);
    private static final LocalDate MARTES = HOY.with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));

    @Autowired private SessionGenerationJob job;
    @Autowired private ClubService clubService;
    @Autowired private JdbcTemplate jdbc;

    private UUID grupoA;
    private UUID grupoB;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        crearClub(CLUB_A, "club-job-a-it");
        crearClub(CLUB_B, "club-job-b-it");
        crearClub(CLUB_VACIO, "club-job-vacio-it");
    }

    @BeforeEach
    void datos() {
        TenantContext.clear();
        modoPublico();
        vaciarDatos();
        grupoA = montarGrupoConHorario(CLUB_A, "Grupo de A");
        grupoB = montarGrupoConHorario(CLUB_B, "Grupo de B");
    }

    @AfterAll
    void fin() {
        TenantContext.clear();
        modoPublico();
        borrarTodo();
    }

    // ----------------------------------------------------------------

    @Test
    @DisplayName("el job genera el calendario de cada club sin que nadie fije el contexto")
    void generaSinPeticion() {
        assertThat(TenantContext.isSet()).as("se arranca sin contexto, como de madrugada").isFalse();

        job.generarProximasSemanas();

        assertThat(sesionesDe(grupoA)).isPositive();
        assertThat(sesionesDe(grupoB)).isPositive();
    }

    /**
     * El contexto es un ThreadLocal y los hilos del planificador se reutilizan:
     * un club que se quedara pegado se lo llevaría el siguiente en ejecutarse.
     */
    @Test
    @DisplayName("el job deja el contexto limpio al terminar")
    void dejaElContextoLimpio() {
        job.generarProximasSemanas();

        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    @DisplayName("cada sesión cae en su club: el job no cruza nada")
    void noCruzaClubes() {
        job.generarProximasSemanas();

        modoPublico();
        Integer descolocadas = jdbc.queryForObject(
                "SELECT count(*) FROM training_sessions s"
                        + " JOIN training_groups g ON g.id = s.group_id"
                        + " WHERE s.club_id <> g.club_id", Integer.class);
        assertThat(descolocadas).isZero();
    }

    /** Es la razón de ser de la idempotencia: el job pasa todas las noches. */
    @Test
    @DisplayName("pasar el job dos noches seguidas no duplica nada")
    void dosPasadasNoDuplican() {
        job.generarProximasSemanas();
        int trasLaPrimera = sesionesDe(grupoA);

        job.generarProximasSemanas();

        assertThat(sesionesDe(grupoA)).isEqualTo(trasLaPrimera);
    }

    @Test
    @DisplayName("un club sin temporada activa no es un error: se pasa de largo")
    void clubSinTemporada() {
        Club vacio = clubService.getById(CLUB_VACIO);

        assertThat(job.generarPara(vacio)).isZero();
        assertThat(TenantContext.isSet()).as("y aun así limpia el contexto").isFalse();
    }

    @Test
    @DisplayName("generar un solo club no toca los demás")
    void generarUnClubNoTocaAlOtro() {
        job.generarPara(clubService.getById(CLUB_A));

        assertThat(sesionesDe(grupoA)).isPositive();
        assertThat(sesionesDe(grupoB)).as("el club B sigue sin calendario").isZero();
    }

    // ----------------------------------------------------------------
    //  Andamiaje
    // ----------------------------------------------------------------

    /** Contado a pelo: aquí lo que se comprueba es el reparto, no la respuesta de la API. */
    private int sesionesDe(UUID grupoId) {
        modoPublico();
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM training_sessions WHERE group_id = ?",
                Integer.class, grupoId);
        return total == null ? 0 : total;
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void vaciarDatos() {
        for (UUID club : new UUID[]{CLUB_A, CLUB_B, CLUB_VACIO}) {
            jdbc.update("DELETE FROM training_sessions WHERE club_id = ?", club);
            jdbc.update("DELETE FROM group_schedules WHERE group_id IN"
                    + " (SELECT id FROM training_groups WHERE club_id = ?)", club);
            jdbc.update("DELETE FROM training_groups WHERE club_id = ?", club);
            jdbc.update("DELETE FROM seasons WHERE club_id = ?", club);
        }
    }

    private void borrarTodo() {
        vaciarDatos();
        jdbc.update("DELETE FROM clubs WHERE id IN (?, ?, ?)", CLUB_A, CLUB_B, CLUB_VACIO);
    }

    private void crearClub(UUID id, String slug) {
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, ?, ?, true, now())", id, "Club " + slug, slug);
    }

    /** Temporada activa, grupo y un horario de los martes. */
    private UUID montarGrupoConHorario(UUID club, String nombre) {
        UUID temporada = UUID.randomUUID();
        UUID grupoId = UUID.randomUUID();
        jdbc.update("INSERT INTO seasons"
                        + " (id, club_id, name, start_date, end_date, active, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, true, now(), now())",
                temporada, club, "Temporada " + nombre, INICIO, FIN);
        jdbc.update("INSERT INTO training_groups"
                        + " (id, club_id, season_id, name, category, level, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, 'ALEVIN', 'COMPETICION', now(), now())",
                grupoId, club, temporada, nombre);
        jdbc.update("INSERT INTO group_schedules"
                        + " (id, group_id, day_of_week, start_time, end_time, modality,"
                        + "  valid_from, valid_until, created_at, updated_at)"
                        + " VALUES (?, ?, 'TUESDAY', '18:00', '19:00', 'SWIMMING', ?, NULL,"
                        + "  now(), now())",
                UUID.randomUUID(), grupoId, MARTES.minusWeeks(1));
        return grupoId;
    }
}
