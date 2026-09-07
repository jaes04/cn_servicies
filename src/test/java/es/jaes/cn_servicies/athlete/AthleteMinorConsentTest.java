package es.jaes.cn_servicies.athlete;

import es.jaes.cn_servicies.guardian.AthleteGuardianRequest;
import es.jaes.cn_servicies.guardian.ConsentEvidenceType;
import es.jaes.cn_servicies.guardian.ConsentService;
import es.jaes.cn_servicies.guardian.ConsentType;
import es.jaes.cn_servicies.guardian.GuardianRelationship;
import es.jaes.cn_servicies.guardian.GuardianRequest;
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
 * Tarea S.1.a, ultimo punto: no se da de alta a un menor de 14 sin el
 * consentimiento de su tutor.
 *
 * <p>Lo que de verdad hay que demostrar aqui no es que salte el error, sino que
 * <b>no queda atleta creado</b> cuando salta. El alta guarda la ficha antes de
 * llegar a la comprobacion, asi que sin transaccion quedaria escrita la fila de
 * un menor sin base legal — justo lo que la regla existe para impedir.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AthleteMinorConsentTest {

    private static final UUID CLUB = UUID.fromString("dddddddd-0000-0000-0000-00000000000d");
    private static final String SLUG = "club-menores-it";

    private static final String DNI_MENOR = "66666661A";
    private static final String DNI_HERMANO = "66666662B";
    private static final String DNI_MAYOR = "66666663C";
    private static final String DNI_TUTOR = "66666664D";

    @Autowired private AthleteService athleteService;
    @Autowired private ConsentService consentService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeAll
    void inicio() {
        modoPublico();
        vaciarClub();
        jdbc.update("DELETE FROM clubs WHERE id = ?", CLUB);
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Menores IT', ?, true, now())", CLUB, SLUG);
    }

    /**
     * Cada test parte del club vacio. Varios cuentan filas —cuantos tutores hay,
     * cuantas fichas con ese DNI— y JUnit no garantiza el orden: compartir datos
     * entre ellos haria que el resultado dependiera de en que orden caigan.
     */
    @BeforeEach
    void contexto() {
        modoPublico();
        vaciarClub();
        TenantContext.set(CLUB);
    }

    @AfterAll
    void fin() {
        TenantContext.clear();
        modoPublico();
        vaciarClub();
        jdbc.update("DELETE FROM clubs WHERE id = ?", CLUB);
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void vaciarClub() {
        jdbc.update("DELETE FROM consents WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM athlete_guardians WHERE guardian_id IN"
                + " (SELECT id FROM guardians WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM guardians WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM athletes WHERE club_id = ?", CLUB);
    }

    private int atletasConDni(String dni) {
        modoPublico();
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM athletes WHERE club_id = ? AND dni = ?",
                Integer.class, CLUB, dni);
        TenantContext.set(CLUB);
        return n;
    }

    private int tutoresEnElClub() {
        modoPublico();
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM guardians WHERE club_id = ?", Integer.class, CLUB);
        TenantContext.set(CLUB);
        return n;
    }

    private AthleteRequest atleta(String dni, LocalDate nacimiento) {
        AthleteRequest request = new AthleteRequest();
        request.setFirstName("Nadador");
        request.setLastName("De Prueba");
        request.setBirthDate(nacimiento);
        request.setDni(dni);
        request.setGender(Gender.MALE);
        return request;
    }

    private AthleteGuardianRequest tutor(boolean tratamiento, boolean imagen) {
        GuardianRequest guardian = new GuardianRequest();
        guardian.setFirstName("Tutora");
        guardian.setLastName("De Prueba");
        guardian.setDni(DNI_TUTOR);
        guardian.setEmail("tutora@it.local");

        AthleteGuardianRequest request = new AthleteGuardianRequest();
        request.setGuardian(guardian);
        request.setRelationship(GuardianRelationship.MOTHER);
        request.setEvidenceType(ConsentEvidenceType.PAPER_FORM);
        request.setDecisionDate(LocalDate.now());
        request.setDataProcessing(tratamiento);
        request.setImage(imagen);
        return request;
    }

    /** Menor de 14 hoy. */
    private LocalDate nacimientoDeMenor() {
        return LocalDate.now().minusYears(10);
    }

    // ----------------------------------------------------------------
    //  1. El bloqueo
    // ----------------------------------------------------------------

    @Test
    @DisplayName("un menor de 14 sin tutor no se da de alta, y no queda ficha suya")
    void elMenorSinTutorNoSeDaDeAlta() {
        AthleteRequest request = atleta(DNI_MENOR, nacimientoDeMenor());

        assertThatThrownBy(() -> athleteService.create(request))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(atletasConDni(DNI_MENOR))
                .as("la ficha del menor no puede quedar escrita sin base legal")
                .isZero();
    }

    @Test
    @DisplayName("sin consentimiento de tratamiento no hay alta, aunque venga el tutor")
    void sinConsentimientoDeTratamientoNoHayAlta() {
        AthleteRequest request = atleta(DNI_MENOR, nacimientoDeMenor());
        request.setGuardian(tutor(false, true));

        assertThatThrownBy(() -> athleteService.create(request))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(atletasConDni(DNI_MENOR)).isZero();
        assertThat(tutoresEnElClub())
                .as("tampoco puede quedar el tutor suelto: el alta es una sola transaccion")
                .isZero();
    }

    // ----------------------------------------------------------------
    //  2. El alta completa
    // ----------------------------------------------------------------

    @Test
    @DisplayName("con tutor y consentimiento, el alta crea ficha, vinculo y los dos consentimientos")
    void elAltaCompletaFunciona() {
        AthleteRequest request = atleta(DNI_MENOR, nacimientoDeMenor());
        request.setGuardian(tutor(true, false));

        AthleteResponse creado = athleteService.create(request);

        assertThat(creado.getId()).isNotNull();
        assertThat(consentService.hasActiveConsent(creado.getId(), ConsentType.DATA_PROCESSING))
                .isTrue();
        assertThat(consentService.hasActiveConsent(creado.getId(), ConsentType.IMAGE))
                .as("dijo que no a la imagen: se registra, pero no queda vigente")
                .isFalse();
        assertThat(consentService.findByAthlete(creado.getId()))
                .as("las dos decisiones constan, la negativa tambien")
                .hasSize(2);
    }

    @Test
    @DisplayName("el segundo hermano reutiliza la ficha del tutor, no crea otra")
    void elHermanoReutilizaElTutor() {
        AthleteRequest primero = atleta(DNI_MENOR, nacimientoDeMenor());
        primero.setGuardian(tutor(true, true));
        athleteService.create(primero);

        AthleteRequest hermano = atleta(DNI_HERMANO, nacimientoDeMenor());
        hermano.setGuardian(tutor(true, true));
        athleteService.create(hermano);

        assertThat(tutoresEnElClub())
                .as("mismo DNI de tutor: una sola ficha para los dos hijos")
                .isEqualTo(1);
    }

    // ----------------------------------------------------------------
    //  3. Mayores de 14 y modificacion
    // ----------------------------------------------------------------

    @Test
    @DisplayName("un mayor de 14 se da de alta sin tutor")
    void elMayorNoNecesitaTutor() {
        AthleteRequest request = atleta(DNI_MAYOR, LocalDate.now().minusYears(16));

        AthleteResponse creado = athleteService.create(request);

        assertThat(creado.getId()).isNotNull();
        assertThat(consentService.findByAthlete(creado.getId())).isEmpty();
    }

    @Test
    @DisplayName("la modificacion rechaza el bloque de tutor en vez de ignorarlo")
    void laModificacionRechazaElBloqueDeTutor() {
        AthleteRequest alta = atleta("66666665E", LocalDate.now().minusYears(20));
        UUID id = athleteService.create(alta).getId();

        AthleteRequest cambio = atleta("66666665E", LocalDate.now().minusYears(20));
        cambio.setGuardian(tutor(true, true));

        assertThatThrownBy(() -> athleteService.update(id, cambio))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
