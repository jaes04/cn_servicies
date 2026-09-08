package es.jaes.cn_servicies.training_group;

import es.jaes.cn_servicies.season.SeasonRequest;
import es.jaes.cn_servicies.season.SeasonService;
import es.jaes.cn_servicies.tenant.TenantContext;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tarea 2.1: horarios recurrentes del grupo.
 *
 * <p>Lo que hay que demostrar son las tres reglas que la 2.2 va a heredar: que
 * la vigencia cae dentro de la temporada, que dos horarios del mismo grupo no se
 * pisan, y sobre todo <b>que {@code validUntil} es el ultimo dia incluido</b>.
 * Ese ultimo es el que, si se equivoca en un dia, no falla un dia sino una
 * sesion por semana durante todo el rango generado.
 *
 * <p>Las temporadas se montan alrededor de {@code LocalDate.now()} y no sobre
 * años fijos: el campo {@code inForce} de la respuesta se calcula contra hoy, y
 * con fechas clavadas el test se pondria rojo solo por pasar el tiempo.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupScheduleServiceTest {

    private static final UUID CLUB = UUID.fromString("44444444-0000-0000-0000-000000000044");
    private static final String SLUG = "club-horarios-it";

    /** El otro club, para el test de aislamiento. */
    private static final UUID CLUB_AJENO = UUID.fromString("44444444-0000-0000-0000-0000000000aa");
    private static final String SLUG_AJENO = "club-horarios-ajeno-it";

    private static final LocalDate HOY = LocalDate.now();
    private static final LocalDate INICIO_TEMPORADA = HOY.minusMonths(3);
    private static final LocalDate FIN_TEMPORADA = HOY.plusMonths(8);

    @Autowired private GroupScheduleService scheduleService;
    @Autowired private TrainingGroupService groupService;
    @Autowired private SeasonService seasonService;
    @Autowired private JdbcTemplate jdbc;

    private UUID grupo;
    private UUID grupoAjeno;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Horarios IT', ?, true, now())", CLUB, SLUG);
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Ajeno IT', ?, true, now())", CLUB_AJENO, SLUG_AJENO);
        grupoAjeno = montarGrupoAjeno();
    }

    @BeforeEach
    void contexto() {
        modoPublico();
        jdbc.update("DELETE FROM group_schedules WHERE group_id IN"
                + " (SELECT id FROM training_groups WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM training_groups WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM seasons WHERE club_id = ?", CLUB);
        TenantContext.set(CLUB);

        UUID temporada = crearTemporada();
        grupo = crearGrupo(temporada);
    }

    @AfterAll
    void fin() {
        TenantContext.clear();
        modoPublico();
        borrarTodo();
    }

    // ----------------------------------------------------------------
    //  1. Varios horarios por grupo
    // ----------------------------------------------------------------

    @Test
    @DisplayName("un grupo tiene varios horarios: lunes y miércoles de agua, martes de seco")
    void variosHorariosPorGrupo() {
        crearHorario(DayOfWeek.MONDAY, "18:00", "19:00", TrainingModality.SWIMMING);
        crearHorario(DayOfWeek.TUESDAY, "17:00", "18:00", TrainingModality.DRYLAND);
        crearHorario(DayOfWeek.WEDNESDAY, "18:00", "19:00", TrainingModality.SWIMMING);

        assertThat(scheduleService.findAll(grupo, null)).hasSize(3);
    }

    /**
     * El dia de la semana se guarda como texto, asi que un {@code ORDER BY} en
     * SQL devolveria FRIDAY, MONDAY, WEDNESDAY. Este test es el que se pone rojo
     * si alguien mueve la ordenacion al repositorio.
     */
    @Test
    @DisplayName("el listado sale de lunes a domingo, no en orden alfabético")
    void ordenPorDiaDeLaSemanaYNoAlfabetico() {
        crearHorario(DayOfWeek.FRIDAY, "18:00", "19:00", TrainingModality.SWIMMING);
        crearHorario(DayOfWeek.MONDAY, "18:00", "19:00", TrainingModality.SWIMMING);
        crearHorario(DayOfWeek.WEDNESDAY, "18:00", "19:00", TrainingModality.SWIMMING);

        assertThat(scheduleService.findAll(grupo, null))
                .extracting(GroupScheduleResponse::getDayOfWeek)
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
    }

    @Test
    @DisplayName("el mismo día a la misma hora en dos modalidades sigue siendo un solape")
    void mismaFranjaDistintaModalidadEsSolape() {
        crearHorario(DayOfWeek.MONDAY, "18:00", "19:00", TrainingModality.SWIMMING);

        assertThatThrownBy(() ->
                crearHorario(DayOfWeek.MONDAY, "18:00", "19:00", TrainingModality.DRYLAND))
                .as("nadie entrena en el agua y en seco a la vez")
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------
    //  2. Horas
    // ----------------------------------------------------------------

    @Test
    @DisplayName("la hora de fin tiene que ser posterior a la de inicio")
    void horarioInvertido() {
        assertThatThrownBy(() ->
                crearHorario(DayOfWeek.MONDAY, "19:00", "18:00", TrainingModality.SWIMMING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un horario de duración cero se rechaza")
    void horarioDeDuracionCero() {
        assertThatThrownBy(() ->
                crearHorario(DayOfWeek.MONDAY, "18:00", "18:00", TrainingModality.SWIMMING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("dos franjas que se tocan no se pisan: 17-18 y 18-19 son encadenables")
    void franjasQueSeTocanSePermiten() {
        crearHorario(DayOfWeek.MONDAY, "17:00", "18:00", TrainingModality.DRYLAND);
        crearHorario(DayOfWeek.MONDAY, "18:00", "19:00", TrainingModality.SWIMMING);

        assertThat(scheduleService.findAll(grupo, null)).hasSize(2);
    }

    @Test
    @DisplayName("dos franjas que se solapan aunque sea una hora se rechazan")
    void franjasQueSeSolapan() {
        crearHorario(DayOfWeek.MONDAY, "17:00", "19:00", TrainingModality.SWIMMING);

        assertThatThrownBy(() ->
                crearHorario(DayOfWeek.MONDAY, "18:00", "20:00", TrainingModality.SWIMMING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la misma franja en días distintos no es solape")
    void mismaFranjaDistintoDia() {
        crearHorario(DayOfWeek.MONDAY, "18:00", "19:00", TrainingModality.SWIMMING);
        crearHorario(DayOfWeek.WEDNESDAY, "18:00", "19:00", TrainingModality.SWIMMING);

        assertThat(scheduleService.findAll(grupo, null)).hasSize(2);
    }

    @Test
    @DisplayName("editar un horario sin moverlo no choca consigo mismo")
    void editarNoChocaConsigoMismo() {
        GroupScheduleResponse horario =
                crearHorario(DayOfWeek.MONDAY, "18:00", "19:00", TrainingModality.SWIMMING);

        GroupScheduleRequest cambio = peticion(DayOfWeek.MONDAY, "18:00", "19:30",
                TrainingModality.SWIMMING, INICIO_TEMPORADA, null);

        assertThat(scheduleService.update(grupo, horario.getId(), cambio).getEndTime())
                .isEqualTo(LocalTime.of(19, 30));
    }

    // ----------------------------------------------------------------
    //  3. Vigencia — el criterio que hereda la 2.2
    // ----------------------------------------------------------------

    @Test
    @DisplayName("validUntil es el ÚLTIMO día de vigencia, incluido")
    void ultimoDiaIncluido() {
        crearConVigencia(INICIO_TEMPORADA, HOY);

        assertThat(idsEnVigor(HOY))
                .as("el día del cierre todavía se entrena")
                .hasSize(1);
        assertThat(idsEnVigor(HOY.plusDays(1)))
                .as("el día siguiente ya no")
                .isEmpty();
    }

    @Test
    @DisplayName("validFrom es el PRIMER día de vigencia, incluido")
    void primerDiaIncluido() {
        crearConVigencia(HOY, null);

        assertThat(idsEnVigor(HOY)).hasSize(1);
        assertThat(idsEnVigor(HOY.minusDays(1))).isEmpty();
    }

    @Test
    @DisplayName("sin validUntil el horario sigue en vigor indefinidamente")
    void sinFinDeVigencia() {
        crearConVigencia(INICIO_TEMPORADA, null);

        assertThat(idsEnVigor(FIN_TEMPORADA)).hasSize(1);
    }

    @Test
    @DisplayName("la vigencia no puede empezar antes de la temporada del grupo")
    void vigenciaAntesDeLaTemporada() {
        assertThatThrownBy(() -> crearConVigencia(INICIO_TEMPORADA.minusDays(1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la vigencia no puede terminar después de la temporada del grupo")
    void vigenciaDespuesDeLaTemporada() {
        assertThatThrownBy(() ->
                crearConVigencia(INICIO_TEMPORADA, FIN_TEMPORADA.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el fin de vigencia no puede ser anterior al inicio")
    void vigenciaInvertida() {
        assertThatThrownBy(() -> crearConVigencia(HOY, HOY.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("mismo día y misma hora en vigencias que no se tocan: son dos trimestres, no un solape")
    void mismaFranjaEnVigenciasDistintas() {
        crearConVigencia(INICIO_TEMPORADA, HOY);
        crearConVigencia(HOY.plusDays(1), FIN_TEMPORADA);

        assertThat(scheduleService.findAll(grupo, null)).hasSize(2);
        assertThat(idsEnVigor(HOY))
                .as("cada uno en vigor en su tramo, nunca los dos a la vez")
                .hasSize(1);
    }

    // ----------------------------------------------------------------
    //  4. Borrado lógico
    // ----------------------------------------------------------------

    @Test
    @DisplayName("borrar un horario lo saca del listado pero no borra la fila")
    void borradoLogico() {
        GroupScheduleResponse horario =
                crearHorario(DayOfWeek.MONDAY, "18:00", "19:00", TrainingModality.SWIMMING);

        scheduleService.softDelete(grupo, horario.getId());

        assertThat(scheduleService.findAll(grupo, null)).isEmpty();
        modoPublico();
        Integer filas = jdbc.queryForObject(
                "SELECT count(*) FROM group_schedules WHERE id = ?",
                Integer.class, horario.getId());
        TenantContext.set(CLUB);
        assertThat(filas).as("la fila sigue ahí, con deleted_at puesto").isEqualTo(1);
    }

    @Test
    @DisplayName("borrar un horario libera la franja para uno nuevo")
    void borrarLiberaLaFranja() {
        GroupScheduleResponse horario =
                crearHorario(DayOfWeek.MONDAY, "18:00", "19:00", TrainingModality.SWIMMING);
        scheduleService.softDelete(grupo, horario.getId());

        crearHorario(DayOfWeek.MONDAY, "18:00", "19:00", TrainingModality.DRYLAND);

        assertThat(scheduleService.findAll(grupo, null)).hasSize(1);
    }

    // ----------------------------------------------------------------
    //  5. Aislamiento entre clubes
    // ----------------------------------------------------------------

    /**
     * {@code group_schedules} no tiene {@code club_id} ni policy de RLS: lo
     * unico que la aisla es que el servicio exija el grupo de la ruta. Si
     * alguien anade un {@code findById} pelado, este test es el que lo dice.
     */
    @Test
    @DisplayName("el grupo de otro club no existe para este: ni sus horarios se listan")
    void grupoAjenoNoSeAlcanza() {
        assertThatThrownBy(() -> scheduleService.findAll(grupoAjeno, null))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("un horario ajeno no se alcanza colgándolo de un grupo propio")
    void horarioAjenoPorGrupoPropio() {
        UUID horarioAjeno = idDelHorarioAjeno();

        assertThatThrownBy(() -> scheduleService.softDelete(grupo, horarioAjeno))
                .isInstanceOf(EntityNotFoundException.class);

        modoPublico();
        String borrado = jdbc.queryForObject(
                "SELECT deleted_at::text FROM group_schedules WHERE id = ?",
                String.class, horarioAjeno);
        TenantContext.set(CLUB);
        assertThat(borrado).as("el horario del otro club sigue intacto").isNull();
    }

    // ----------------------------------------------------------------
    //  Andamiaje
    // ----------------------------------------------------------------

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void borrarTodo() {
        jdbc.update("DELETE FROM group_schedules WHERE group_id IN"
                + " (SELECT id FROM training_groups WHERE club_id IN (?, ?))", CLUB, CLUB_AJENO);
        jdbc.update("DELETE FROM training_groups WHERE club_id IN (?, ?)", CLUB, CLUB_AJENO);
        jdbc.update("DELETE FROM seasons WHERE club_id IN (?, ?)", CLUB, CLUB_AJENO);
        jdbc.update("DELETE FROM clubs WHERE id IN (?, ?)", CLUB, CLUB_AJENO);
    }

    private UUID crearTemporada() {
        SeasonRequest request = new SeasonRequest();
        request.setName("Temporada horarios IT");
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

    private GroupScheduleRequest peticion(DayOfWeek dia, String desde, String hasta,
                                          TrainingModality modalidad,
                                          LocalDate vigenteDesde, LocalDate vigenteHasta) {
        GroupScheduleRequest request = new GroupScheduleRequest();
        request.setDayOfWeek(dia);
        request.setStartTime(LocalTime.parse(desde));
        request.setEndTime(LocalTime.parse(hasta));
        request.setModality(modalidad);
        request.setValidFrom(vigenteDesde);
        request.setValidUntil(vigenteHasta);
        return request;
    }

    private GroupScheduleResponse crearHorario(DayOfWeek dia, String desde, String hasta,
                                               TrainingModality modalidad) {
        return scheduleService.create(grupo,
                peticion(dia, desde, hasta, modalidad, INICIO_TEMPORADA, null));
    }

    /** Siempre el mismo dia y la misma franja: lo que se prueba es la vigencia. */
    private GroupScheduleResponse crearConVigencia(LocalDate desde, LocalDate hasta) {
        return scheduleService.create(grupo, peticion(DayOfWeek.MONDAY, "18:00", "19:00",
                TrainingModality.SWIMMING, desde, hasta));
    }

    /**
     * Ids y no entidades: {@code @Data} genera un {@code toString()} que recorre
     * las relaciones LAZY, y el mensaje de un assert fallido se convertiria en un
     * {@code EntityNotFoundException} que no cuenta lo que pasaba.
     */
    private List<UUID> idsEnVigor(LocalDate fecha) {
        return scheduleService.inForceOn(grupo, fecha).stream()
                .map(GroupSchedule::getId)
                .toList();
    }

    /** Grupo y horario del otro club, insertados a pelo para no tocar el TenantContext. */
    private UUID montarGrupoAjeno() {
        UUID temporada = UUID.randomUUID();
        UUID grupoId = UUID.randomUUID();
        jdbc.update("INSERT INTO seasons"
                        + " (id, club_id, name, start_date, end_date, active, created_at, updated_at)"
                        + " VALUES (?, ?, 'Temporada ajena IT', ?, ?, false, now(), now())",
                temporada, CLUB_AJENO, INICIO_TEMPORADA, FIN_TEMPORADA);
        jdbc.update("INSERT INTO training_groups"
                        + " (id, club_id, season_id, name, category, level, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'Grupo ajeno', 'ALEVIN', 'COMPETICION', now(), now())",
                grupoId, CLUB_AJENO, temporada);
        jdbc.update("INSERT INTO group_schedules"
                        + " (id, group_id, day_of_week, start_time, end_time, modality,"
                        + "  valid_from, valid_until, created_at, updated_at)"
                        + " VALUES (?, ?, 'MONDAY', '18:00', '19:00', 'SWIMMING', ?, NULL, now(), now())",
                UUID.randomUUID(), grupoId, INICIO_TEMPORADA);
        return grupoId;
    }

    private UUID idDelHorarioAjeno() {
        modoPublico();
        UUID id = jdbc.queryForObject(
                "SELECT id FROM group_schedules WHERE group_id = ?", UUID.class, grupoAjeno);
        TenantContext.set(CLUB);
        return id;
    }
}
