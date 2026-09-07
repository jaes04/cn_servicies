package es.jaes.cn_servicies.season;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tarea 1.1: temporadas.
 *
 * <p>Lo que hay que demostrar es la regla de <b>una sola activa por club</b>, y
 * demostrarla en las dos capas: que el servicio apaga la anterior al encender
 * otra, y que si alguien llegara por otra via la base tampoco lo permitiria.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SeasonServiceTest {

    private static final UUID CLUB = UUID.fromString("22222222-0000-0000-0000-000000000022");
    private static final String SLUG = "club-temporadas-it";

    @Autowired private SeasonService seasonService;
    @Autowired private ClubService clubService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Temporadas IT', ?, true, now())", CLUB, SLUG);
    }

    @BeforeEach
    void contexto() {
        modoPublico();
        jdbc.update("DELETE FROM seasons WHERE club_id = ?", CLUB);
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

    private void borrarTodo() {
        jdbc.update("DELETE FROM seasons WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM clubs WHERE id = ?", CLUB);
    }

    private SeasonResponse crear(String nombre, int anio) {
        SeasonRequest request = new SeasonRequest();
        request.setName(nombre);
        request.setStartDate(LocalDate.of(anio, 9, 1));
        request.setEndDate(LocalDate.of(anio + 1, 8, 31));
        return seasonService.create(request);
    }

    private int activasEnLaBase() {
        modoPublico();
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM seasons WHERE club_id = ? AND active", Integer.class, CLUB);
        TenantContext.set(CLUB);
        return n;
    }

    // ----------------------------------------------------------------
    //  1. Una sola activa
    // ----------------------------------------------------------------

    @Test
    @DisplayName("una temporada nace apagada: crear no es activar")
    void naceApagada() {
        SeasonResponse creada = crear("2024/2025", 2024);

        assertThat(creada.isActive()).isFalse();
        assertThat(activasEnLaBase()).isZero();
    }

    @Test
    @DisplayName("activar una apaga la anterior")
    void activarApagaLaAnterior() {
        SeasonResponse vieja = crear("2024/2025", 2024);
        SeasonResponse nueva = crear("2025/2026", 2025);
        seasonService.activate(vieja.getId());

        seasonService.activate(nueva.getId());

        assertThat(activasEnLaBase()).isEqualTo(1);
        assertThat(seasonService.findActive().getId()).isEqualTo(nueva.getId());
        assertThat(seasonService.findById(vieja.getId()).isActive()).isFalse();
    }

    @Test
    @DisplayName("la base tampoco deja dos activas, aunque se llegue por fuera del servicio")
    void laBaseTampocoDejaDosActivas() {
        SeasonResponse una = crear("2024/2025", 2024);
        SeasonResponse otra = crear("2025/2026", 2025);
        seasonService.activate(una.getId());

        // Sin pasar por el servicio: es lo que haria una consulta futura mal
        // escrita, y el indice unico parcial es lo unico que queda para pararla.
        modoPublico();
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE seasons SET active = true WHERE id = ?", otra.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        TenantContext.set(CLUB);
    }

    @Test
    @DisplayName("varias apagadas conviven sin problema: la restricción es solo sobre las activas")
    void variasApagadasConviven() {
        crear("2022/2023", 2022);
        crear("2023/2024", 2023);
        crear("2024/2025", 2024);

        assertThat(seasonService.findAll()).hasSize(3);
        assertThat(activasEnLaBase()).isZero();
    }

    // ----------------------------------------------------------------
    //  2. Validación
    // ----------------------------------------------------------------

    @Test
    @DisplayName("una temporada que termina antes de empezar se rechaza")
    void fechasAlReves() {
        SeasonRequest request = new SeasonRequest();
        request.setName("Imposible");
        request.setStartDate(LocalDate.of(2026, 9, 1));
        request.setEndDate(LocalDate.of(2026, 8, 31));

        assertThatThrownBy(() -> seasonService.create(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("dos temporadas del mismo club no pueden llamarse igual")
    void nombreRepetido() {
        crear("2024/2025", 2024);

        assertThatThrownBy(() -> crear("2024/2025", 2025))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sin temporada activa, preguntar por ella es un 404 y no un null")
    void sinActivaEs404() {
        crear("2024/2025", 2024);

        assertThatThrownBy(() -> seasonService.findActive())
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    // ----------------------------------------------------------------
    //  3. La siembra
    // ----------------------------------------------------------------

    @Test
    @DisplayName("la siembra crea la temporada en curso, activa, y no repite en el siguiente arranque")
    void laSiembraEsIdempotente() {
        Club club = clubService.getById(CLUB);

        seasonService.ensureCurrentSeason(club);
        seasonService.ensureCurrentSeason(club);

        assertThat(seasonService.findAll())
                .as("dos arranques no pueden dejar dos temporadas")
                .hasSize(1);
        assertThat(seasonService.findActive().getName())
                .isEqualTo(nombreEsperado());
        assertThat(activasEnLaBase()).isEqualTo(1);
    }

    @Test
    @DisplayName("la siembra no toca nada si el club ya tiene temporadas")
    void laSiembraRespetaLoQueHaya() {
        crear("2020/2021", 2020);

        seasonService.ensureCurrentSeason(clubService.getById(CLUB));

        assertThat(seasonService.findAll()).hasSize(1);
        assertThat(activasEnLaBase())
                .as("no activa nada por su cuenta sobre un club que ya tenía temporadas")
                .isZero();
    }

    /** De septiembre en adelante la temporada es la que empieza este año. */
    private String nombreEsperado() {
        LocalDate hoy = LocalDate.now();
        int inicio = hoy.getMonthValue() >= 9 ? hoy.getYear() : hoy.getYear() - 1;
        return inicio + "/" + (inicio + 1);
    }
}
