package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.season.SeasonRequest;
import es.jaes.cn_servicies.season.SeasonService;
import es.jaes.cn_servicies.tenant.TenantContext;
import es.jaes.cn_servicies.training_group.GroupCategory;
import es.jaes.cn_servicies.training_group.GroupLevel;
import es.jaes.cn_servicies.training_group.GroupScheduleRequest;
import es.jaes.cn_servicies.training_group.GroupScheduleService;
import es.jaes.cn_servicies.training_group.TrainingGroupRequest;
import es.jaes.cn_servicies.training_group.TrainingGroupService;
import es.jaes.cn_servicies.training_group.TrainingModality;
import jakarta.persistence.EntityNotFoundException;
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
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tarea 2.2.b: calendario de excepciones y reactivacion.
 *
 * <p>Lo que hay que demostrar son las tres decisiones del bloque: que un cierre
 * <b>no impide generar sino que cancela</b>, que la modalidad lo acota —cerrar
 * la piscina no tumba el gimnasio— y que <b>no reescribe el pasado</b>.
 *
 * <p>Y la que las sostiene todas: que reactivar gana. Si el generador volviera a
 * cancelar lo que alguien reactivo, la reactivacion no serviria para nada.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClubClosureServiceTest {

    private static final UUID CLUB = UUID.fromString("aaaa1111-0000-0000-0000-0000000011aa");
    private static final String SLUG = "club-cierres-it";

    private static final UUID CLUB_AJENO = UUID.fromString("aaaa1111-0000-0000-0000-0000000022aa");
    private static final String SLUG_AJENO = "club-cierres-ajeno-it";

    private static final LocalDate HOY = LocalDate.now();
    private static final LocalDate INICIO_TEMPORADA = HOY.minusMonths(3);
    private static final LocalDate FIN_TEMPORADA = HOY.plusMonths(8);

    private static final LocalDate MARTES = HOY.with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
    private static final LocalDate JUEVES = MARTES.plusDays(2);
    private static final LocalDate FIN_RANGO = MARTES.plusWeeks(4).minusDays(1);

    /** Un martes ya pasado, para lo que no se puede reescribir. */
    private static final LocalDate MARTES_PASADO = MARTES.minusWeeks(3);

    @Autowired private ClubClosureService closureService;
    @Autowired private TrainingSessionService sessionService;
    @Autowired private GroupScheduleService scheduleService;
    @Autowired private TrainingGroupService groupService;
    @Autowired private SeasonService seasonService;
    @Autowired private JdbcTemplate jdbc;

    private UUID grupo;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Cierres IT', ?, true, now())", CLUB, SLUG);
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Cierres Ajeno IT', ?, true, now())", CLUB_AJENO, SLUG_AJENO);
    }

    @BeforeEach
    void contexto() {
        modoPublico();
        vaciar(CLUB);
        vaciar(CLUB_AJENO);
        TenantContext.set(CLUB);

        grupo = crearGrupo(crearTemporada());
        crearHorario(DayOfWeek.TUESDAY, "18:00", "19:00", TrainingModality.SWIMMING);
        crearHorario(DayOfWeek.THURSDAY, "17:00", "18:00", TrainingModality.DRYLAND);
    }

    @AfterAll
    void fin() {
        TenantContext.clear();
        modoPublico();
        borrarTodo();
    }

    // ----------------------------------------------------------------
    //  1. El cierre no impide generar: cancela
    // ----------------------------------------------------------------

    @Test
    @DisplayName("la sesión de un día cerrado se genera igual, pero nace cancelada")
    void naceCancelada() {
        cerrar(MARTES, MARTES, CancellationReason.HOLIDAY, null);

        SessionGenerationResponse resumen = generar();

        assertThat(resumen.getBornCancelled())
                .as("el martes y nada más: el jueves no está cerrado")
                .isEqualTo(1);
        assertThat(sesionesDe(MARTES))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.getStatus()).isEqualTo(SessionStatus.CANCELLED);
                    assertThat(s.getCancellationReason()).isEqualTo(CancellationReason.HOLIDAY);
                });
    }

    /**
     * Es la diferencia entre no generar y generar cancelado: el día sigue en el
     * calendario, explicando por qué no se entrena.
     */
    @Test
    @DisplayName("el día cerrado sigue apareciendo en el calendario")
    void elDiaSigueEnElCalendario() {
        cerrar(MARTES, MARTES, CancellationReason.HOLIDAY, null);
        generar();

        assertThat(sesionesDe(MARTES)).hasSize(1);
    }

    // ----------------------------------------------------------------
    //  2. La modalidad acota el cierre
    // ----------------------------------------------------------------

    @Test
    @DisplayName("cerrar la piscina no cancela el entrenamiento en seco")
    void elCierreDePiscinaNoTumbaElSeco() {
        cerrar(MARTES, JUEVES, CancellationReason.POOL_CLOSURE, TrainingModality.SWIMMING);

        generar();

        assertThat(sesionesDe(MARTES)).singleElement()
                .satisfies(s -> assertThat(s.getStatus()).isEqualTo(SessionStatus.CANCELLED));
        assertThat(sesionesDe(JUEVES)).singleElement()
                .as("el gimnasio sigue abierto")
                .satisfies(s -> assertThat(s.getStatus()).isEqualTo(SessionStatus.SCHEDULED));
    }

    @Test
    @DisplayName("un cierre sin modalidad se lleva todo por delante")
    void elCierreSinModalidadTumbaTodo() {
        cerrar(MARTES, JUEVES, CancellationReason.HOLIDAY, null);

        generar();

        assertThat(sesionesDe(MARTES)).singleElement()
                .satisfies(s -> assertThat(s.getStatus()).isEqualTo(SessionStatus.CANCELLED));
        assertThat(sesionesDe(JUEVES)).singleElement()
                .satisfies(s -> assertThat(s.getStatus()).isEqualTo(SessionStatus.CANCELLED));
    }

    // ----------------------------------------------------------------
    //  3. Sobre lo ya generado
    // ----------------------------------------------------------------

    @Test
    @DisplayName("declarar un cierre cancela las sesiones que ya estaban generadas")
    void cancelaLoYaGenerado() {
        generar();
        assertThat(sesionesDe(MARTES)).singleElement()
                .satisfies(s -> assertThat(s.getStatus()).isEqualTo(SessionStatus.SCHEDULED));

        ClubClosureResponse cierre =
                cerrar(MARTES, MARTES, CancellationReason.POOL_CLOSURE, null);

        assertThat(cierre.getCancelledSessions())
                .as("quien se equivoca de fechas tiene que enterarse en ese momento")
                .isEqualTo(1);
        assertThat(sesionesDe(MARTES)).singleElement()
                .satisfies(s -> assertThat(s.getStatus()).isEqualTo(SessionStatus.CANCELLED));
    }

    /**
     * Si el 12 de marzo hubo entrenamiento y se pasó lista, declarar hoy que
     * aquel día fue festivo no puede borrarlo.
     */
    @Test
    @DisplayName("un cierre no reescribe las sesiones que ya pasaron")
    void noReescribeElPasado() {
        sessionService.generate(grupo, MARTES_PASADO, FIN_RANGO);

        ClubClosureResponse cierre =
                cerrar(MARTES_PASADO, MARTES_PASADO, CancellationReason.HOLIDAY, null);

        assertThat(cierre.getCancelledSessions()).isZero();
        assertThat(sesionesDe(MARTES_PASADO)).singleElement()
                .satisfies(s -> assertThat(s.getStatus()).isEqualTo(SessionStatus.SCHEDULED));
    }

    @Test
    @DisplayName("un cierre que empieza en el pasado y llega a hoy solo toca de hoy en adelante")
    void elCierreAcaballoSoloTocaElFuturo() {
        sessionService.generate(grupo, MARTES_PASADO, FIN_RANGO);

        cerrar(MARTES_PASADO, MARTES, CancellationReason.POOL_CLOSURE, null);

        assertThat(sesionesDe(MARTES_PASADO)).singleElement()
                .as("lo de hace tres semanas se queda como estaba")
                .satisfies(s -> assertThat(s.getStatus()).isEqualTo(SessionStatus.SCHEDULED));
        assertThat(sesionesDe(MARTES)).singleElement()
                .satisfies(s -> assertThat(s.getStatus()).isEqualTo(SessionStatus.CANCELLED));
    }

    @Test
    @DisplayName("un cierre no pisa el motivo de una sesión ya cancelada a mano")
    void noPisaLaCanceladaAMano() {
        generar();
        UUID martes = sesionesDe(MARTES).get(0).getId();
        sessionService.cancel(martes, CancellationReason.COACH_UNAVAILABLE);

        cerrar(MARTES, MARTES, CancellationReason.HOLIDAY, null);

        assertThat(sessionService.findById(martes).getCancellationReason())
                .as("se canceló porque no había entrenador, y eso es lo que pasó")
                .isEqualTo(CancellationReason.COACH_UNAVAILABLE);
    }

    // ----------------------------------------------------------------
    //  4. Reactivación
    // ----------------------------------------------------------------

    @Test
    @DisplayName("reactivar devuelve la sesión a programada y le quita el motivo")
    void reactivar() {
        cerrar(MARTES, MARTES, CancellationReason.HOLIDAY, null);
        generar();
        UUID martes = sesionesDe(MARTES).get(0).getId();

        TrainingSessionResponse reactivada = sessionService.reactivate(martes);

        assertThat(reactivada.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(reactivada.getCancellationReason()).isNull();
    }

    /**
     * Sin esto la reactivación no serviría de nada: el job volvería a tumbarla
     * en la siguiente pasada.
     */
    @Test
    @DisplayName("reactivar gana: volver a generar no la vuelve a cancelar")
    void reactivarGanaSobreElCierre() {
        cerrar(MARTES, MARTES, CancellationReason.HOLIDAY, null);
        generar();
        UUID martes = sesionesDe(MARTES).get(0).getId();
        sessionService.reactivate(martes);

        generar();

        assertThat(sessionService.findById(martes).getStatus())
                .isEqualTo(SessionStatus.SCHEDULED);
    }

    @Test
    @DisplayName("reactivar una sesión que no está cancelada se rechaza")
    void reactivarLaQueNoEstaCancelada() {
        generar();
        UUID martes = sesionesDe(MARTES).get(0).getId();

        assertThatThrownBy(() -> sessionService.reactivate(martes))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("borrar el cierre no reactiva nada: eso se decide sesión a sesión")
    void borrarElCierreNoReactiva() {
        ClubClosureResponse cierre = cerrar(MARTES, MARTES, CancellationReason.HOLIDAY, null);
        generar();

        closureService.delete(cierre.getId());

        assertThat(sesionesDe(MARTES)).singleElement()
                .satisfies(s -> assertThat(s.getStatus()).isEqualTo(SessionStatus.CANCELLED));
    }

    @Test
    @DisplayName("pero borrado el cierre, lo que se genere después ya no nace cancelado")
    void borrarElCierreLiberaLoQueVenga() {
        ClubClosureResponse cierre = cerrar(MARTES, MARTES, CancellationReason.HOLIDAY, null);
        closureService.delete(cierre.getId());

        assertThat(generar().getBornCancelled()).isZero();
    }

    // ----------------------------------------------------------------
    //  5. Validación y aislamiento
    // ----------------------------------------------------------------

    @Test
    @DisplayName("un cierre con las fechas invertidas se rechaza")
    void fechasInvertidas() {
        assertThatThrownBy(() ->
                cerrar(JUEVES, MARTES, CancellationReason.HOLIDAY, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un cierre desmesurado se rechaza: cancelaría el curso entero")
    void cierreDemasiadoLargo() {
        assertThatThrownBy(() ->
                cerrar(MARTES, MARTES.plusYears(3), CancellationReason.HOLIDAY, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Quien tapa esto es <b>el filtro de Hibernate</b>, no la policy: el
     * generador consulta los cierres por rango, que es una consulta normal y no
     * una carga por clave primaria. Comprobado: con
     * {@code DISABLE ROW LEVEL SECURITY} este test sigue en verde.
     *
     * <p>Lo que solo tapa RLS es {@link #borrarElCierreAjenoNoSePuede}.
     */
    @Test
    @DisplayName("el cierre de otro club no cancela nuestras sesiones")
    void elCierreAjenoNoNosAfecta() {
        crearCierreAjeno(MARTES);

        assertThat(generar().getBornCancelled()).isZero();
        assertThat(sesionesDe(MARTES)).singleElement()
                .satisfies(s -> assertThat(s.getStatus()).isEqualTo(SessionStatus.SCHEDULED));
    }

    /**
     * <b>Este sí depende de la policy.</b> El borrado carga el cierre por su id,
     * y los filtros de Hibernate no se aplican a las cargas por clave primaria:
     * sin RLS, esto encontraría el cierre del otro club y lo borraría de verdad.
     */
    @Test
    @DisplayName("no se puede borrar el cierre de otro club aun sabiendo su id")
    void borrarElCierreAjenoNoSePuede() {
        UUID ajeno = crearCierreAjeno(MARTES);

        assertThatThrownBy(() -> closureService.delete(ajeno))
                .isInstanceOf(EntityNotFoundException.class);

        modoPublico();
        Integer sigue = jdbc.queryForObject(
                "SELECT count(*) FROM club_closures WHERE id = ?", Integer.class, ajeno);
        TenantContext.set(CLUB);
        assertThat(sigue).as("el cierre del otro club sigue en su sitio").isEqualTo(1);
    }

    @Test
    @DisplayName("el listado solo trae los cierres del propio club")
    void elListadoNoTraeLosAjenos() {
        crearCierreAjeno(MARTES);
        cerrar(JUEVES, JUEVES, CancellationReason.HOLIDAY, null);

        assertThat(closureService.findAll(INICIO_TEMPORADA, FIN_TEMPORADA))
                .singleElement()
                .satisfies(c -> assertThat(c.getStartDate()).isEqualTo(JUEVES));
    }

    // ----------------------------------------------------------------
    //  Andamiaje
    // ----------------------------------------------------------------

    private SessionGenerationResponse generar() {
        return sessionService.generate(grupo, MARTES, FIN_RANGO);
    }

    private List<TrainingSessionResponse> sesionesDe(LocalDate fecha) {
        return sessionService.findAll(grupo, fecha, fecha);
    }

    private ClubClosureResponse cerrar(LocalDate desde, LocalDate hasta,
                                       CancellationReason motivo, TrainingModality modalidad) {
        ClubClosureRequest request = new ClubClosureRequest();
        request.setStartDate(desde);
        request.setEndDate(hasta);
        request.setReason(motivo);
        request.setModality(modalidad);
        return closureService.create(request);
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void vaciar(UUID club) {
        jdbc.update("DELETE FROM club_closures WHERE club_id = ?", club);
        jdbc.update("DELETE FROM training_sessions WHERE club_id = ?", club);
        jdbc.update("DELETE FROM group_schedules WHERE group_id IN"
                + " (SELECT id FROM training_groups WHERE club_id = ?)", club);
        jdbc.update("DELETE FROM training_groups WHERE club_id = ?", club);
        jdbc.update("DELETE FROM seasons WHERE club_id = ?", club);
    }

    private void borrarTodo() {
        vaciar(CLUB);
        vaciar(CLUB_AJENO);
        jdbc.update("DELETE FROM clubs WHERE id IN (?, ?)", CLUB, CLUB_AJENO);
    }

    private UUID crearTemporada() {
        SeasonRequest request = new SeasonRequest();
        request.setName("Temporada cierres IT");
        request.setStartDate(INICIO_TEMPORADA);
        request.setEndDate(FIN_TEMPORADA);
        return seasonService.create(request).getId();
    }

    private UUID crearGrupo(UUID temporada) {
        TrainingGroupRequest request = new TrainingGroupRequest();
        request.setSeasonId(temporada);
        request.setName("Alevín A");
        request.setCategory(GroupCategory.ALEVIN);
        request.setLevel(GroupLevel.COMPETICION);
        return groupService.create(request).getId();
    }

    private void crearHorario(DayOfWeek dia, String desde, String hasta,
                              TrainingModality modalidad) {
        GroupScheduleRequest request = new GroupScheduleRequest();
        request.setDayOfWeek(dia);
        request.setStartTime(LocalTime.parse(desde));
        request.setEndTime(LocalTime.parse(hasta));
        request.setModality(modalidad);
        request.setValidFrom(INICIO_TEMPORADA);
        scheduleService.create(grupo, request);
    }

    /** Cierre del otro club, insertado a pelo para no tocar el TenantContext. */
    private UUID crearCierreAjeno(LocalDate fecha) {
        modoPublico();
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO club_closures"
                        + " (id, club_id, start_date, end_date, reason, modality,"
                        + "  created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, 'HOLIDAY', NULL, now(), now())",
                id, CLUB_AJENO, fecha, fecha);
        TenantContext.set(CLUB);
        return id;
    }
}
