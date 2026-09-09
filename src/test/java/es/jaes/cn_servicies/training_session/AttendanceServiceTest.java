package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.season.SeasonRequest;
import es.jaes.cn_servicies.season.SeasonService;
import es.jaes.cn_servicies.tenant.TenantContext;
import es.jaes.cn_servicies.training_group.AthleteGroupService;
import es.jaes.cn_servicies.training_group.GroupCategory;
import es.jaes.cn_servicies.training_group.GroupLevel;
import es.jaes.cn_servicies.training_group.GroupScheduleRequest;
import es.jaes.cn_servicies.training_group.GroupScheduleService;
import es.jaes.cn_servicies.training_group.LeaveReason;
import es.jaes.cn_servicies.training_group.TrainingGroupRequest;
import es.jaes.cn_servicies.training_group.TrainingGroupService;
import es.jaes.cn_servicies.training_group.TrainingModality;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tarea 2.3: pasar lista.
 *
 * <p>Lo que hay que demostrar: que la lista son <b>los atletas de la fecha de la
 * sesion</b> y no los de hoy —es la pieza que hereda de la 1.3—, que dos
 * entrenadores pasando lista a la vez no dejan filas duplicadas, y que guardar
 * marca la sesion como realizada salvo que sea futura.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttendanceServiceTest {

    private static final UUID CLUB = UUID.fromString("cccc3333-0000-0000-0000-0000000033cc");
    private static final String SLUG = "club-asistencia-it";
    private static final String ENTRENADOR = "coach_asistencia_it";

    private static final LocalDate HOY = LocalDate.now();
    private static final LocalDate INICIO = HOY.minusMonths(3);
    private static final LocalDate FIN = HOY.plusMonths(8);

    /** Un martes ya pasado y el proximo, para separar el ayer del mañana. */
    private static final LocalDate MARTES_PROXIMO =
            HOY.with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
    private static final LocalDate MARTES_PASADO = MARTES_PROXIMO.minusWeeks(3);

    @Autowired private AttendanceService attendanceService;
    @Autowired private TrainingSessionService sessionService;
    @Autowired private AthleteGroupService membershipService;
    @Autowired private GroupScheduleService scheduleService;
    @Autowired private TrainingGroupService groupService;
    @Autowired private SeasonService seasonService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private UUID grupo;
    private UUID ana;
    private UUID bruno;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Asistencia IT', ?, true, now())", CLUB, SLUG);
        crearEntrenador();
    }

    @BeforeEach
    void contexto() {
        modoPublico();
        vaciar();
        TenantContext.set(CLUB);
        autenticarComoEntrenador();

        grupo = crearGrupo(crearTemporada());
        crearHorario();
        ana = crearAtleta("Ana", "10000001A");
        bruno = crearAtleta("Bruno", "10000002B");
        membershipService.assign(ana, grupo, INICIO, null);
        membershipService.assign(bruno, grupo, INICIO, null);
    }

    @AfterAll
    void fin() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        modoPublico();
        borrarTodo();
    }

    // ----------------------------------------------------------------
    //  1. El roster
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el roster trae la sesión y sus atletas en una sola llamada")
    void rosterCompleto() {
        UUID sesion = generarYCoger(MARTES_PASADO);

        RosterResponse roster = attendanceService.roster(sesion);

        assertThat(roster.getDate()).isEqualTo(MARTES_PASADO);
        assertThat(roster.getGroupName()).isEqualTo("Alevín A");
        assertThat(roster.getModality()).isEqualTo(TrainingModality.SWIMMING);
        assertThat(roster.getAthletes())
                .extracting(RosterEntryResponse::getAthleteName)
                .containsExactlyInAnyOrder("Ana Nadadora", "Bruno Nadador");
        assertThat(roster.getAthletes())
                .as("todavía nadie ha pasado lista")
                .allSatisfy(a -> assertThat(a.getStatus()).isNull());
    }

    /**
     * La pieza que la 2.3 hereda de la 1.3: pasar lista de un entrenamiento de
     * hace tres semanas tiene que mostrar a quien estaba entonces.
     */
    @Test
    @DisplayName("la lista son los atletas de la fecha de la sesión, no los de hoy")
    void losAtletasSonLosDeEseDia() {
        UUID sesionPasada = generarYCoger(MARTES_PASADO);
        // Bruno se va del grupo justo después de aquel martes.
        membershipService.leave(bruno, grupo, MARTES_PASADO, LeaveReason.GROUP_CHANGE);

        assertThat(attendanceService.roster(sesionPasada).getAthletes())
                .as("aquel día Bruno todavía entrenaba: leftOn es el último día incluido")
                .extracting(RosterEntryResponse::getAthleteName)
                .contains("Bruno Nadador");

        UUID sesionFutura = generarYCoger(MARTES_PROXIMO);
        assertThat(attendanceService.roster(sesionFutura).getAthletes())
                .as("pero la semana que viene ya no")
                .extracting(RosterEntryResponse::getAthleteName)
                .doesNotContain("Bruno Nadador");
    }

    // ----------------------------------------------------------------
    //  2. Guardar
    // ----------------------------------------------------------------

    @Test
    @DisplayName("guardar la lista deja el estado y quién lo registró")
    void guardar() {
        UUID sesion = generarYCoger(MARTES_PASADO);

        RosterResponse roster = pasarLista(sesion,
                ana, AttendanceStatus.PRESENT,
                bruno, AttendanceStatus.ABSENT);

        assertThat(roster.getAthletes())
                .filteredOn(a -> a.getAthleteId().equals(ana))
                .singleElement()
                .satisfies(a -> {
                    assertThat(a.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
                    assertThat(a.getRegisteredBy()).isEqualTo(ENTRENADOR);
                });
    }

    @Test
    @DisplayName("no hace falta mandar a todos: lo que no viene se queda como estaba")
    void guardadoIncremental() {
        UUID sesion = generarYCoger(MARTES_PASADO);
        pasarLista(sesion, ana, AttendanceStatus.PRESENT, bruno, AttendanceStatus.ABSENT);

        RosterResponse roster = pasarLista(sesion, bruno, AttendanceStatus.LATE);

        assertThat(estadoDe(roster, ana))
                .as("a Ana no la han vuelto a nombrar y sigue presente")
                .isEqualTo(AttendanceStatus.PRESENT);
        assertThat(estadoDe(roster, bruno)).isEqualTo(AttendanceStatus.LATE);
    }

    @Test
    @DisplayName("corregir una falta pisa el estado anterior, no crea otra fila")
    void corregirNoDuplica() {
        UUID sesion = generarYCoger(MARTES_PASADO);
        pasarLista(sesion, ana, AttendanceStatus.ABSENT);

        pasarLista(sesion, ana, AttendanceStatus.PRESENT);

        modoPublico();
        Integer filas = jdbc.queryForObject(
                "SELECT count(*) FROM attendance WHERE session_id = ? AND athlete_id = ?",
                Integer.class, sesion, ana);
        TenantContext.set(CLUB);
        assertThat(filas).isEqualTo(1);
    }

    @Test
    @DisplayName("un atleta que no estaba en el grupo ese día se rechaza, y no entra ninguno")
    void atletaAjenoAlGrupo() {
        UUID sesion = generarYCoger(MARTES_PASADO);
        UUID intruso = crearAtleta("Intrusa", "10000003C");

        assertThatThrownBy(() -> pasarLista(sesion,
                ana, AttendanceStatus.PRESENT,
                intruso, AttendanceStatus.PRESENT))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(estadoDe(attendanceService.roster(sesion), ana))
                .as("media lista es peor que ninguna")
                .isNull();
    }

    @Test
    @DisplayName("no se pasa lista de una sesión cancelada")
    void sesionCancelada() {
        UUID sesion = generarYCoger(MARTES_PASADO);
        sessionService.cancel(sesion, CancellationReason.POOL_CLOSURE);

        assertThatThrownBy(() -> pasarLista(sesion, ana, AttendanceStatus.PRESENT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------
    //  3. El estado de la sesión
    // ----------------------------------------------------------------

    @Test
    @DisplayName("pasar lista marca la sesión como realizada")
    void marcaRealizada() {
        UUID sesion = generarYCoger(MARTES_PASADO);

        pasarLista(sesion, ana, AttendanceStatus.PRESENT);

        assertThat(sessionService.findById(sesion).getStatus())
                .isEqualTo(SessionStatus.DONE);
    }

    /**
     * Marcarla realizada antes de tiempo la contaría como celebrada en los
     * informes y la sacaría del alcance de la regeneración, que no toca las
     * DONE: cambiar el horario dejaría de arrastrarla.
     */
    @Test
    @DisplayName("pasar lista por adelantado se permite, pero no marca realizada una sesión futura")
    void listaAdelantadaNoMarcaRealizada() {
        UUID sesion = generarYCoger(MARTES_PROXIMO);

        pasarLista(sesion, ana, AttendanceStatus.PRESENT);

        assertThat(estadoDe(attendanceService.roster(sesion), ana))
                .as("la lista sí se guarda")
                .isEqualTo(AttendanceStatus.PRESENT);
        assertThat(sessionService.findById(sesion).getStatus())
                .as("pero el entrenamiento aún no ha ocurrido")
                .isEqualTo(SessionStatus.SCHEDULED);
    }

    // ----------------------------------------------------------------
    //  4. Concurrencia
    // ----------------------------------------------------------------

    /**
     * Dos entrenadores al borde de la misma piscina, guardando a la vez. Con un
     * leer-y-escribir los dos verían la fila vacía y los dos insertarían; lo que
     * lo evita es el {@code ON CONFLICT} del repositorio, apoyado en el índice
     * único.
     */
    @Test
    @DisplayName("dos entrenadores pasando lista a la vez no dejan filas duplicadas")
    void dosEntrenadoresALaVez() throws InterruptedException {
        UUID sesion = generarYCoger(MARTES_PASADO);

        CountDownLatch alaVez = new CountDownLatch(1);
        CountDownLatch terminados = new CountDownLatch(2);

        Runnable pasar = () -> {
            TenantContext.set(CLUB);
            autenticarComoEntrenador();
            try {
                alaVez.await();
                attendanceService.save(sesion, peticion(ana, AttendanceStatus.PRESENT));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                TenantContext.clear();
                SecurityContextHolder.clearContext();
                terminados.countDown();
            }
        };

        new Thread(pasar).start();
        new Thread(pasar).start();
        alaVez.countDown();
        assertThat(terminados.await(20, TimeUnit.SECONDS)).as("los dos terminaron").isTrue();

        modoPublico();
        Integer filas = jdbc.queryForObject(
                "SELECT count(*) FROM attendance WHERE session_id = ? AND athlete_id = ?",
                Integer.class, sesion, ana);
        TenantContext.set(CLUB);
        assertThat(filas).as("una sola fila, gane quien gane").isEqualTo(1);
    }

    // ----------------------------------------------------------------
    //  Andamiaje
    // ----------------------------------------------------------------

    private RosterResponse pasarLista(UUID sesion, Object... paresAtletaEstado) {
        return attendanceService.save(sesion, peticion(paresAtletaEstado));
    }

    private AttendanceRequest peticion(Object... paresAtletaEstado) {
        List<AttendanceEntryRequest> entradas = new java.util.ArrayList<>();
        for (int i = 0; i < paresAtletaEstado.length; i += 2) {
            AttendanceEntryRequest entrada = new AttendanceEntryRequest();
            entrada.setAthleteId((UUID) paresAtletaEstado[i]);
            entrada.setStatus((AttendanceStatus) paresAtletaEstado[i + 1]);
            entradas.add(entrada);
        }
        AttendanceRequest request = new AttendanceRequest();
        request.setEntries(entradas);
        return request;
    }

    /**
     * El {@code findFirst} va antes del {@code map} a propósito: al revés, un
     * atleta sin estado mete un {@code null} en el stream y {@code findFirst}
     * revienta con un NullPointer en vez de devolver vacío.
     */
    private AttendanceStatus estadoDe(RosterResponse roster, UUID atleta) {
        return roster.getAthletes().stream()
                .filter(a -> a.getAthleteId().equals(atleta))
                .findFirst()
                .map(RosterEntryResponse::getStatus)
                .orElse(null);
    }

    /** Genera el calendario de ese día y devuelve el id de la sesión que salga. */
    private UUID generarYCoger(LocalDate fecha) {
        sessionService.generate(grupo, fecha, fecha);
        return sessionService.findAll(grupo, fecha, fecha).get(0).getId();
    }

    private void autenticarComoEntrenador() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ENTRENADOR, null, List.of()));
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void vaciar() {
        jdbc.update("DELETE FROM attendance WHERE session_id IN"
                + " (SELECT id FROM training_sessions WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM training_sessions WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM athlete_groups WHERE athlete_id IN"
                + " (SELECT id FROM athletes WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM group_schedules WHERE group_id IN"
                + " (SELECT id FROM training_groups WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM training_groups WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM athletes WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM seasons WHERE club_id = ?", CLUB);
    }

    private void borrarTodo() {
        vaciar();
        jdbc.update("DELETE FROM user_roles WHERE user_id IN"
                + " (SELECT id FROM users WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM users WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM clubs WHERE id = ?", CLUB);
    }

    private void crearEntrenador() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users"
                        + " (id, club_id, username, email, password_hash, blocked, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, false, now(), now())",
                id, CLUB, ENTRENADOR, ENTRENADOR + "@it.local",
                passwordEncoder.encode("clave-de-prueba-it"));
        jdbc.update("INSERT INTO user_roles (user_id, role_id)"
                + " SELECT ?, id FROM roles WHERE name = 'ROLE_TECHNICAL_STAFF'", id);
    }

    private UUID crearTemporada() {
        SeasonRequest request = new SeasonRequest();
        request.setName("Temporada asistencia IT");
        request.setStartDate(INICIO);
        request.setEndDate(FIN);
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

    private void crearHorario() {
        GroupScheduleRequest request = new GroupScheduleRequest();
        request.setDayOfWeek(DayOfWeek.TUESDAY);
        request.setStartTime(LocalTime.of(18, 0));
        request.setEndTime(LocalTime.of(19, 0));
        request.setModality(TrainingModality.SWIMMING);
        request.setValidFrom(INICIO);
        scheduleService.create(grupo, request);
    }

    private UUID crearAtleta(String nombre, String dni) {
        UUID id = UUID.randomUUID();
        modoPublico();
        jdbc.update("INSERT INTO athletes"
                        + " (id, club_id, first_name, last_name, birth_date, dni, gender_id,"
                        + "  created_at, updated_at)"
                        + " SELECT ?, ?, ?, ?, DATE '2013-01-01', ?, g.id, now(), now()"
                        + " FROM genders g WHERE g.name = 'MALE'",
                id, CLUB, nombre, nombre.equals("Ana") ? "Nadadora" : "Nadador", dni);
        TenantContext.set(CLUB);
        return id;
    }
}
