package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.season.SeasonRequest;
import es.jaes.cn_servicies.season.SeasonService;
import es.jaes.cn_servicies.tenant.TenantContext;
import es.jaes.cn_servicies.training_group.GroupCategory;
import es.jaes.cn_servicies.training_group.GroupLevel;
import es.jaes.cn_servicies.training_group.GroupScheduleRequest;
import es.jaes.cn_servicies.training_group.GroupScheduleResponse;
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
 * Tarea 2.2: generacion de sesiones.
 *
 * <p>El roadmap dice que si el job duplica sesiones el club pierde la confianza
 * en el sistema entero, asi que <b>la idempotencia es lo que hay que
 * demostrar</b>: generar dos veces el mismo rango, generar sobre una sesion
 * cancelada, y generar despues de cambiar el horario.
 *
 * <p>El otro criterio que se prueba aqui es que la sesion <b>copia</b> hora y
 * modalidad en vez de leerlas del horario: es lo que impide que corregir un
 * horario en marzo reescriba lo que se entreno en febrero.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrainingSessionServiceTest {

    private static final UUID CLUB = UUID.fromString("88888888-0000-0000-0000-000000000088");
    private static final String SLUG = "club-sesiones-it";

    private static final UUID CLUB_AJENO = UUID.fromString("88888888-0000-0000-0000-0000000000aa");
    private static final String SLUG_AJENO = "club-sesiones-ajeno-it";

    private static final LocalDate HOY = LocalDate.now();
    private static final LocalDate INICIO_TEMPORADA = HOY.minusMonths(3);
    private static final LocalDate FIN_TEMPORADA = HOY.plusMonths(8);

    /** El primer martes de hoy en adelante, y cuatro semanas contadas desde el. */
    private static final LocalDate PRIMER_MARTES =
            HOY.with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
    private static final LocalDate FIN_RANGO = PRIMER_MARTES.plusWeeks(4).minusDays(1);

    @Autowired private TrainingSessionService sessionService;
    @Autowired private GroupScheduleService scheduleService;
    @Autowired private TrainingGroupService groupService;
    @Autowired private SeasonService seasonService;
    @Autowired private JdbcTemplate jdbc;

    private UUID grupo;
    private UUID horario;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Sesiones IT', ?, true, now())", CLUB, SLUG);
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Ajeno Sesiones IT', ?, true, now())", CLUB_AJENO, SLUG_AJENO);
    }

    @BeforeEach
    void contexto() {
        modoPublico();
        vaciar(CLUB);
        // También el ajeno: los dos tests de aislamiento lo montan cada uno, y
        // sin vaciarlo el segundo choca con el único de nombre de temporada.
        vaciar(CLUB_AJENO);
        TenantContext.set(CLUB);

        UUID temporada = crearTemporada(CLUB);
        grupo = crearGrupo(temporada);
        horario = crearHorario(DayOfWeek.TUESDAY, "18:00", "19:00",
                TrainingModality.SWIMMING, INICIO_TEMPORADA, null);
    }

    @AfterAll
    void fin() {
        TenantContext.clear();
        modoPublico();
        borrarTodo();
    }

    // ----------------------------------------------------------------
    //  1. Generación
    // ----------------------------------------------------------------

    @Test
    @DisplayName("genera una sesión por cada martes del rango, y ninguna los demás días")
    void generaSoloLosDiasQueTocan() {
        SessionGenerationResponse resumen = generar();

        assertThat(resumen.getCreated()).isEqualTo(4);
        assertThat(sesiones())
                .hasSize(4)
                .allSatisfy(s -> assertThat(s.getDate().getDayOfWeek())
                        .isEqualTo(DayOfWeek.TUESDAY));
    }

    @Test
    @DisplayName("la sesión copia hora y modalidad del horario")
    void copiaDelHorario() {
        generar();

        assertThat(sesiones()).first().satisfies(s -> {
            assertThat(s.getStartTime()).isEqualTo(LocalTime.of(18, 0));
            assertThat(s.getEndTime()).isEqualTo(LocalTime.of(19, 0));
            assertThat(s.getModality()).isEqualTo(TrainingModality.SWIMMING);
            assertThat(s.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
            assertThat(s.isOneOff()).isFalse();
        });
    }

    @Test
    @DisplayName("no genera fuera de la vigencia del horario")
    void respetaLaVigencia() {
        // El horario deja de estar vigente después del primer martes.
        scheduleService.update(grupo, horario, peticionHorario(DayOfWeek.TUESDAY, "18:00", "19:00",
                TrainingModality.SWIMMING, INICIO_TEMPORADA, PRIMER_MARTES));

        assertThat(generar().getCreated())
                .as("el último día de vigencia sí se entrena; el martes siguiente ya no")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("un grupo sin horarios no genera nada, y no es un error")
    void sinHorarios() {
        scheduleService.softDelete(grupo, horario);

        assertThat(generar().getCreated()).isZero();
    }

    // ----------------------------------------------------------------
    //  2. Idempotencia — lo que el roadmap dice que no es opcional
    // ----------------------------------------------------------------

    @Test
    @DisplayName("generar dos veces el mismo rango no duplica ni una sesión")
    void generarDosVecesNoDuplica() {
        assertThat(generar().getCreated()).isEqualTo(4);

        SessionGenerationResponse segunda = generar();

        assertThat(segunda.getCreated()).as("nada nuevo").isZero();
        assertThat(segunda.getAlreadyExisted()).as("las cuatro ya estaban").isEqualTo(4);
        assertThat(sesiones()).hasSize(4);
    }

    @Test
    @DisplayName("los rangos que se solapan tampoco duplican: es el caso normal del job")
    void rangosSolapadosNoDuplican() {
        sessionService.generate(grupo, PRIMER_MARTES, PRIMER_MARTES.plusWeeks(2).minusDays(1));
        sessionService.generate(grupo, PRIMER_MARTES.plusWeeks(1), FIN_RANGO);

        assertThat(sesiones()).hasSize(4);
    }

    /**
     * El caso que mas duele: alguien cancela el entrenamiento del 14, el job
     * vuelve a pasar por esa semana y lo repone como si nada.
     */
    @Test
    @DisplayName("una sesión cancelada no resucita al volver a generar")
    void laCanceladaNoResucita() {
        generar();
        UUID primera = sesiones().get(0).getId();
        sessionService.cancel(primera, CancellationReason.POOL_CLOSURE);

        SessionGenerationResponse segunda = generar();

        assertThat(segunda.getCreated()).isZero();
        assertThat(sesiones()).hasSize(4);
        assertThat(sessionService.findById(primera).getStatus())
                .isEqualTo(SessionStatus.CANCELLED);
    }

    @Test
    @DisplayName("cambiar el horario no reescribe las sesiones ya generadas")
    void cambiarElHorarioNoTocaElPasado() {
        generar();

        scheduleService.update(grupo, horario, peticionHorario(DayOfWeek.TUESDAY, "19:00", "20:00",
                TrainingModality.DRYLAND, INICIO_TEMPORADA, null));
        generar();

        assertThat(sesiones())
                .as("se entrenó a las 18:00 y en el agua, y eso no cambia porque el horario cambie")
                .hasSize(4)
                .allSatisfy(s -> {
                    assertThat(s.getStartTime()).isEqualTo(LocalTime.of(18, 0));
                    assertThat(s.getModality()).isEqualTo(TrainingModality.SWIMMING);
                });
    }

    // ----------------------------------------------------------------
    //  3. Rangos
    // ----------------------------------------------------------------

    @Test
    @DisplayName("un rango invertido se rechaza")
    void rangoInvertido() {
        assertThatThrownBy(() -> sessionService.generate(grupo, FIN_RANGO, PRIMER_MARTES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un rango desmesurado se rechaza: un cero de más son cientos de miles de filas")
    void rangoDemasiadoLargo() {
        assertThatThrownBy(() ->
                sessionService.generate(grupo, PRIMER_MARTES, PRIMER_MARTES.plusYears(3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------
    //  4. Cancelación
    // ----------------------------------------------------------------

    @Test
    @DisplayName("cancelar guarda el motivo y no borra la fila")
    void cancelarNoBorra() {
        generar();
        UUID primera = sesiones().get(0).getId();

        TrainingSessionResponse cancelada =
                sessionService.cancel(primera, CancellationReason.HOLIDAY);

        assertThat(cancelada.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(cancelada.getCancellationReason()).isEqualTo(CancellationReason.HOLIDAY);
        assertThat(sesiones()).as("sigue en el calendario, explicando el hueco").hasSize(4);
    }

    @Test
    @DisplayName("cancelar dos veces se rechaza")
    void cancelarDosVeces() {
        generar();
        UUID primera = sesiones().get(0).getId();
        sessionService.cancel(primera, CancellationReason.HOLIDAY);

        assertThatThrownBy(() -> sessionService.cancel(primera, CancellationReason.WEATHER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------
    //  5. Sesiones puntuales
    // ----------------------------------------------------------------

    @Test
    @DisplayName("una sesión puntual no sale de ningún horario")
    void sesionPuntual() {
        TrainingSessionResponse puntual = crearPuntual(PRIMER_MARTES.plusDays(3), "10:00", "13:00");

        assertThat(puntual.isOneOff()).isTrue();
        assertThat(puntual.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
    }

    @Test
    @DisplayName("dos sesiones puntuales el mismo día se permiten: el índice único ignora los nulos")
    void dosPuntualesElMismoDia() {
        crearPuntual(PRIMER_MARTES.plusDays(3), "10:00", "13:00");
        crearPuntual(PRIMER_MARTES.plusDays(3), "17:00", "19:00");

        assertThat(sesiones()).hasSize(2);
    }

    @Test
    @DisplayName("una puntual el mismo día que un entrenamiento no impide generarlo")
    void laPuntualNoBloqueaLaGeneracion() {
        crearPuntual(PRIMER_MARTES, "10:00", "13:00");

        assertThat(generar().getCreated()).isEqualTo(4);
        assertThat(sesiones()).as("la competición y el entrenamiento de ese martes").hasSize(5);
    }

    @Test
    @DisplayName("una sesión puntual fuera de la temporada se rechaza")
    void puntualFueraDeTemporada() {
        assertThatThrownBy(() ->
                crearPuntual(FIN_TEMPORADA.plusDays(1), "10:00", "13:00"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("una sesión puntual con la hora invertida se rechaza")
    void puntualInvertida() {
        assertThatThrownBy(() -> crearPuntual(PRIMER_MARTES.plusDays(3), "13:00", "10:00"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------
    //  6. Regeneración al cambiar un horario (2.2.b)
    // ----------------------------------------------------------------

    /** Un martes ya pasado, dentro de la temporada. */
    private static final LocalDate MARTES_PASADO = PRIMER_MARTES.minusWeeks(3);

    @Test
    @DisplayName("cambiar el horario rehace las sesiones futuras y deja intactas las pasadas")
    void regeneraSoloElFuturo() {
        sessionService.generate(grupo, MARTES_PASADO, FIN_RANGO);

        scheduleService.update(grupo, horario, peticionHorario(DayOfWeek.TUESDAY, "19:00", "20:00",
                TrainingModality.SWIMMING, INICIO_TEMPORADA, null));
        sessionService.regenerateForSchedule(grupo, horario);

        List<TrainingSessionResponse> todas =
                sessionService.findAll(grupo, INICIO_TEMPORADA, FIN_TEMPORADA);

        assertThat(todas)
                .filteredOn(s -> s.getDate().isBefore(HOY))
                .as("lo que ya se entrenó se quedó a las 18:00, y así sigue")
                .isNotEmpty()
                .allSatisfy(s -> assertThat(s.getStartTime()).isEqualTo(LocalTime.of(18, 0)));

        assertThat(todas)
                .filteredOn(s -> s.getDate().isAfter(HOY))
                .as("lo que aún no ha pasado se rehace con el horario nuevo")
                .isNotEmpty()
                .allSatisfy(s -> assertThat(s.getStartTime()).isEqualTo(LocalTime.of(19, 0)));
    }

    @Test
    @DisplayName("al cambiar el horario, la cancelación de un día que ya no existe se va con él")
    void laCanceladaFuturaSeVaConElHorario() {
        generar();
        TrainingSessionResponse futura = sessionService
                .findAll(grupo, HOY.plusDays(1), FIN_RANGO).get(0);
        sessionService.cancel(futura.getId(), CancellationReason.COACH_UNAVAILABLE);

        // El grupo se muda del martes al miércoles: ese martes deja de existir.
        scheduleService.update(grupo, horario, peticionHorario(DayOfWeek.WEDNESDAY, "18:00", "19:00",
                TrainingModality.SWIMMING, INICIO_TEMPORADA, null));
        sessionService.regenerateForSchedule(grupo, horario);

        assertThat(sessionService.findAll(grupo, INICIO_TEMPORADA, FIN_TEMPORADA))
                .extracting(TrainingSessionResponse::getId)
                .doesNotContain(futura.getId());
    }

    @Test
    @DisplayName("descartar un horario se lleva sus sesiones futuras, no las pasadas")
    void descartarSeLlevaSoloElFuturo() {
        sessionService.generate(grupo, MARTES_PASADO, FIN_RANGO);
        long pasadas = sessionService.findAll(grupo, INICIO_TEMPORADA, HOY).size();

        int descartadas = sessionService.discardFutureForSchedule(horario);

        assertThat(descartadas).isPositive();
        assertThat(sessionService.findAll(grupo, HOY.plusDays(1), FIN_TEMPORADA))
                .as("un horario que desaparece no puede seguir poniendo entrenamientos")
                .isEmpty();
        assertThat(sessionService.findAll(grupo, INICIO_TEMPORADA, HOY))
                .as("y el registro de lo que se entrenó sigue ahí")
                .hasSize((int) pasadas);
    }

    // ----------------------------------------------------------------
    //  7. Aislamiento
    // ----------------------------------------------------------------

    /**
     * <b>Ojo con lo que demuestra este test y lo que no.</b> Pasa en verde
     * aunque {@code training_sessions} no tenga policy ninguna, porque al
     * construir la respuesta se toca el nombre del grupo y lo que salta es el
     * proxy del grupo ajeno, que si esta tapado. Lo comprobado es que el
     * servicio no devuelve la sesion de otro club, no por que.
     *
     * <p>Que la policy de esta tabla existe y funciona lo demuestra
     * {@code TenantIsolationTest.findByIdNoDevuelveSesionesDeOtroClub}, que va
     * contra el repositorio y se pone en rojo con
     * {@code DISABLE ROW LEVEL SECURITY}.
     */
    @Test
    @DisplayName("la sesión de otro club no se alcanza por su id")
    void sesionAjenaPorId() {
        UUID ajena = crearSesionAjena();

        assertThatThrownBy(() -> sessionService.findById(ajena))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("tampoco se puede cancelar la sesión de otro club")
    void cancelarSesionAjena() {
        UUID ajena = crearSesionAjena();

        assertThatThrownBy(() -> sessionService.cancel(ajena, CancellationReason.HOLIDAY))
                .isInstanceOf(EntityNotFoundException.class);

        modoPublico();
        String estado = jdbc.queryForObject(
                "SELECT status FROM training_sessions WHERE id = ?", String.class, ajena);
        TenantContext.set(CLUB);
        assertThat(estado).as("la sesión del otro club sigue programada").isEqualTo("SCHEDULED");
    }

    // ----------------------------------------------------------------
    //  Andamiaje
    // ----------------------------------------------------------------

    private SessionGenerationResponse generar() {
        return sessionService.generate(grupo, PRIMER_MARTES, FIN_RANGO);
    }

    /** Ordenadas por fecha, que es como las devuelve el repositorio. */
    private List<TrainingSessionResponse> sesiones() {
        return sessionService.findAll(grupo, INICIO_TEMPORADA, FIN_TEMPORADA);
    }

    private TrainingSessionResponse crearPuntual(LocalDate fecha, String desde, String hasta) {
        TrainingSessionRequest request = new TrainingSessionRequest();
        request.setDate(fecha);
        request.setStartTime(LocalTime.parse(desde));
        request.setEndTime(LocalTime.parse(hasta));
        request.setModality(TrainingModality.SWIMMING);
        return sessionService.createOneOff(grupo, request);
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void vaciar(UUID club) {
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

    private UUID crearTemporada(UUID club) {
        if (club.equals(CLUB)) {
            SeasonRequest request = new SeasonRequest();
            request.setName("Temporada sesiones IT");
            request.setStartDate(INICIO_TEMPORADA);
            request.setEndDate(FIN_TEMPORADA);
            return seasonService.create(request).getId();
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO seasons"
                        + " (id, club_id, name, start_date, end_date, active, created_at, updated_at)"
                        + " VALUES (?, ?, 'Temporada ajena IT', ?, ?, true, now(), now())",
                id, club, INICIO_TEMPORADA, FIN_TEMPORADA);
        return id;
    }

    private UUID crearGrupo(UUID temporada) {
        TrainingGroupRequest request = new TrainingGroupRequest();
        request.setSeasonId(temporada);
        request.setName("Alevín A");
        request.setCategory(GroupCategory.ALEVIN);
        request.setLevel(GroupLevel.COMPETICION);
        return groupService.create(request).getId();
    }

    private GroupScheduleRequest peticionHorario(DayOfWeek dia, String desde, String hasta,
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

    private UUID crearHorario(DayOfWeek dia, String desde, String hasta,
                              TrainingModality modalidad,
                              LocalDate vigenteDesde, LocalDate vigenteHasta) {
        GroupScheduleResponse creado = scheduleService.create(grupo,
                peticionHorario(dia, desde, hasta, modalidad, vigenteDesde, vigenteHasta));
        return creado.getId();
    }

    /** Sesión del otro club, insertada a pelo para no tocar el TenantContext. */
    private UUID crearSesionAjena() {
        modoPublico();
        UUID temporada = crearTemporada(CLUB_AJENO);
        UUID grupoAjeno = UUID.randomUUID();
        UUID sesion = UUID.randomUUID();
        jdbc.update("INSERT INTO training_groups"
                        + " (id, club_id, season_id, name, category, level, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'Grupo ajeno', 'ALEVIN', 'COMPETICION', now(), now())",
                grupoAjeno, CLUB_AJENO, temporada);
        jdbc.update("INSERT INTO training_sessions"
                        + " (id, club_id, group_id, schedule_id, session_date, start_time, end_time,"
                        + "  modality, status, created_at, updated_at)"
                        + " VALUES (?, ?, ?, NULL, ?, '18:00', '19:00', 'SWIMMING', 'SCHEDULED',"
                        + "  now(), now())",
                sesion, CLUB_AJENO, grupoAjeno, PRIMER_MARTES);
        TenantContext.set(CLUB);
        return sesion;
    }
}
