package es.jaes.cn_servicies.athlete;

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

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bloque 3b: el documento de identidad del atleta pasa a ser opcional.
 *
 * <p>Hasta ahora el DNI era obligatorio y unico por club, y eso tenia dos
 * consecuencias que se pagaban en el alta: un menor sin DNI obligaba a inventarse
 * uno, y el segundo atleta sin DNI ya no cabia. Lo que hay que demostrar es lo
 * contrario de cada una — que caben todos los que no tienen documento — sin
 * perder lo que protegia el indice: que la misma persona no tenga dos fichas.
 *
 * <p>Los atletas son todos mayores de 14, para que el consentimiento de tutor no
 * se cruce con lo que se prueba aqui.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AthleteIdentityTest {

    private static final UUID CLUB = UUID.fromString("cccc8888-0000-0000-0000-000000008811");
    private static final String SLUG = "club-identidad-it";
    private static final LocalDate NACIMIENTO = LocalDate.of(2000, 1, 1);

    @Autowired private AthleteService athleteService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Identidad IT', ?, true, now())", CLUB, SLUG);
    }

    @BeforeEach
    void limpio() {
        modoPublico();
        jdbc.update("DELETE FROM athletes WHERE club_id = ?", CLUB);
        TenantContext.set(CLUB);
    }

    @AfterAll
    void fin() {
        TenantContext.clear();
        modoPublico();
        borrarTodo();
    }

    // ----------------------------------------------------------------
    //  1. Sin documento se puede dar de alta, y caben todos
    // ----------------------------------------------------------------

    @Test
    @DisplayName("un atleta sin documento se da de alta, y su documento sale como nulo")
    void sinDocumento() {
        AthleteResponse creado = athleteService.create(atleta("Ana", null, NACIMIENTO));

        assertThat(creado.getDni()).isNull();
    }

    /** El caso que motivó el cambio: con el indice unico, el segundo sin DNI no cabia. */
    @Test
    @DisplayName("dos atletas distintos sin documento no chocan")
    void dosSinDocumentoNoChocan() {
        athleteService.create(atleta("Ana", null, NACIMIENTO));
        athleteService.create(atleta("Bruno", null, NACIMIENTO));

        modoPublico();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM athletes WHERE club_id = ?", Integer.class, CLUB))
                .isEqualTo(2);
    }

    /**
     * Un vacio se guarda como nulo. Si se guardara como cadena vacia, el indice
     * unico si consideraria iguales dos vacios y el segundo chocaria.
     */
    @Test
    @DisplayName("un documento vacío o de espacios se guarda como nulo, y dos así no chocan")
    void vacioEsNulo() {
        AthleteResponse conEspacios = athleteService.create(atleta("Ana", "   ", NACIMIENTO));
        AthleteResponse vacio = athleteService.create(atleta("Bruno", "", NACIMIENTO));

        assertThat(conEspacios.getDni()).isNull();
        assertThat(vacio.getDni()).isNull();
    }

    // ----------------------------------------------------------------
    //  2. La misma persona sigue sin poder tener dos fichas
    // ----------------------------------------------------------------

    @Test
    @DisplayName("sin documento, mismo nombre, apellidos y nacimiento es la misma persona")
    void mismaPersonaSinDocumento() {
        athleteService.create(atleta("Ana", null, NACIMIENTO));

        assertThatThrownBy(() -> athleteService.create(atleta("ana", null, NACIMIENTO)))
                .as("sin distinguir mayúsculas: es la forma habitual de repetir una ficha")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mismo nombre");
    }

    /**
     * Mas amplio que "entre fichas sin documento": si alguien ya esta fichado con
     * su DNI y se le vuelve a dar de alta sin el, sigue siendo la misma persona.
     */
    @Test
    @DisplayName("también si la ficha que ya existe sí tiene documento")
    void mismaPersonaUnaConDocumento() {
        athleteService.create(atleta("Ana", "12345678Z", NACIMIENTO));

        assertThatThrownBy(() -> athleteService.create(atleta("Ana", null, NACIMIENTO)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("mismo nombre con otra fecha de nacimiento es otra persona")
    void mismoNombreOtraFecha() {
        athleteService.create(atleta("Ana", null, NACIMIENTO));
        athleteService.create(atleta("Ana", null, NACIMIENTO.plusYears(1)));

        modoPublico();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM athletes WHERE club_id = ?", Integer.class, CLUB))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("el mismo documento en dos fichas sigue siendo un error")
    void documentoRepetido() {
        athleteService.create(atleta("Ana", "12345678Z", NACIMIENTO));

        assertThatThrownBy(() -> athleteService.create(atleta("Bruno", "12345678Z", NACIMIENTO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DNI");
    }

    // ----------------------------------------------------------------
    //  3. NIE, pasaporte y normalización
    // ----------------------------------------------------------------

    @Test
    @DisplayName("un NIE en minúsculas y con espacios se guarda limpio, y cuenta como el mismo documento")
    void seNormaliza() {
        AthleteResponse creado = athleteService.create(atleta("Ana", " x1234567l ", NACIMIENTO));

        assertThat(creado.getDni()).isEqualTo("X1234567L");
        assertThatThrownBy(() -> athleteService.create(atleta("Bruno", "X1234567L", NACIMIENTO)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un pasaporte se acepta")
    void pasaporte() {
        assertThat(athleteService.create(atleta("Ana", "PAA123456", NACIMIENTO)).getDni())
                .isEqualTo("PAA123456");
    }

    // ----------------------------------------------------------------
    //  4. Edición
    // ----------------------------------------------------------------

    /** Antes del bloque 3b, editar una ficha sin DNI habria reventado con NullPointerException. */
    @Test
    @DisplayName("se puede quitar el documento de una ficha, y editar una sin documento")
    void quitarElDocumento() {
        UUID id = athleteService.create(atleta("Ana", "12345678Z", NACIMIENTO)).getId();

        AthleteResponse sinDocumento = athleteService.update(id, atleta("Ana", null, NACIMIENTO));
        AthleteResponse otraVez = athleteService.update(id, atleta("Ana", null, NACIMIENTO));

        assertThat(sinDocumento.getDni()).isNull();
        assertThat(otraVez.getDni())
                .as("editar una ficha sin documento no la cuenta como duplicada de sí misma")
                .isNull();
    }

    @Test
    @DisplayName("editar una ficha para convertirla en otra que ya existe se rechaza")
    void edicionQueDuplica() {
        athleteService.create(atleta("Ana", null, NACIMIENTO));
        UUID bruno = athleteService.create(atleta("Bruno", null, NACIMIENTO)).getId();

        assertThatThrownBy(() -> athleteService.update(bruno, atleta("Ana", null, NACIMIENTO)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------

    private AthleteRequest atleta(String nombre, String documento, LocalDate nacimiento) {
        AthleteRequest request = new AthleteRequest();
        request.setFirstName(nombre);
        request.setLastName("Identidad");
        request.setBirthDate(nacimiento);
        request.setDni(documento);
        request.setGender(Gender.FEMALE);
        return request;
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void borrarTodo() {
        jdbc.update("DELETE FROM athletes WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM clubs WHERE id = ?", CLUB);
    }
}
