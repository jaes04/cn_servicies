package es.jaes.cn_servicies.guardian;

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
 * Tarea S.1.a: el consentimiento de menores.
 *
 * <p>Lo que se comprueba aqui no es que el CRUD funcione, sino las tres reglas
 * que hacen que el registro sirva como prueba: que revocar no borre, que solo
 * consienta quien esta vinculado, y que la edad se mida en la fecha de la
 * decision y no en la de hoy.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsentServiceTest {

    private static final UUID CLUB = UUID.fromString("cccccccc-0000-0000-0000-00000000000c");
    private static final String SLUG = "club-consent-it";

    @Autowired private ConsentService consentService;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private JdbcTemplate jdbc;

    private UUID atleta;
    private UUID tutor;
    private UUID tutorSinVinculo;

    // ----------------------------------------------------------------
    //  Datos de prueba
    // ----------------------------------------------------------------

    @BeforeAll
    void crearDatos() {
        modoPublico();
        borrarDatos();

        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Consent IT', ?, true, now())", CLUB, SLUG);

        atleta = crearAtleta("77777771A", LocalDate.of(2015, 3, 10));
        tutor = crearTutor("77777772B");
        tutorSinVinculo = crearTutor("77777773C");
        vincular(atleta, tutor);
    }

    @BeforeEach
    void contexto() {
        // El servicio saca el club del contexto, igual que en una peticion real.
        TenantContext.set(CLUB);
    }

    @AfterAll
    void fin() {
        TenantContext.clear();
        modoPublico();
        borrarDatos();
    }

    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void borrarDatos() {
        jdbc.update("DELETE FROM consents WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM athlete_guardians WHERE guardian_id IN"
                + " (SELECT id FROM guardians WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM guardians WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM athletes WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM clubs WHERE id = ?", CLUB);
    }

    private UUID crearAtleta(String dni, LocalDate nacimiento) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO athletes"
                        + " (id, club_id, first_name, last_name, birth_date, dni, gender_id, created_at, updated_at)"
                        + " SELECT ?, ?, 'Atleta', 'Consent', ?, ?, g.id, now(), now()"
                        + " FROM genders g WHERE g.name = 'MALE'",
                id, CLUB, nacimiento, dni);
        return id;
    }

    private UUID crearTutor(String dni) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO guardians"
                        + " (id, club_id, first_name, last_name, dni, email, created_at, updated_at)"
                        + " VALUES (?, ?, 'Tutor', 'Consent', ?, ?, now(), now())",
                id, CLUB, dni, dni.toLowerCase() + "@it.local");
        return id;
    }

    private void vincular(UUID atletaId, UUID tutorId) {
        jdbc.update("INSERT INTO athlete_guardians"
                        + " (id, athlete_id, guardian_id, relationship, created_at)"
                        + " VALUES (?, ?, ?, 'MOTHER', now())",
                UUID.randomUUID(), atletaId, tutorId);
    }

    private ConsentRequest peticion(ConsentType tipo, boolean otorgado, ConsentEvidenceType evidencia) {
        ConsentRequest request = new ConsentRequest();
        request.setGuardianId(tutor);
        request.setType(tipo);
        request.setGranted(otorgado);
        request.setDecisionDate(LocalDate.now());
        request.setEvidenceType(evidencia);
        return request;
    }

    // ----------------------------------------------------------------
    //  1. Registro y vigencia
    // ----------------------------------------------------------------

    @Test
    @DisplayName("un consentimiento otorgado queda vigente para su finalidad, y solo para esa")
    void otorgarDejaVigenteSoloEsaFinalidad() {
        Consent consent = consentService.record(atleta,
                peticion(ConsentType.DATA_PROCESSING, true, ConsentEvidenceType.PAPER_FORM), null);

        assertThat(consent.isActive()).isTrue();
        assertThat(consentService.hasActiveConsent(atleta, ConsentType.DATA_PROCESSING)).isTrue();
        assertThat(consentService.hasActiveConsent(atleta, ConsentType.IMAGE))
                .as("la imagen se consiente aparte: no la arrastra el consentimiento general")
                .isFalse();

        consentService.revoke(consent.getId());
    }

    @Test
    @DisplayName("una negativa registrada no deja nada vigente, pero deja constancia")
    void laNegativaSeGuarda() {
        Consent consent = consentService.record(atleta,
                peticion(ConsentType.IMAGE, false, ConsentEvidenceType.PAPER_FORM), null);

        assertThat(consent.isActive()).isFalse();
        assertThat(consentService.hasActiveConsent(atleta, ConsentType.IMAGE)).isFalse();
        assertThat(consentRepository.findById(consent.getId()))
                .as("la negativa tiene que constar: no es lo mismo que no haber preguntado")
                .isPresent();
    }

    // ----------------------------------------------------------------
    //  2. Revocacion
    // ----------------------------------------------------------------

    @Test
    @DisplayName("revocar deja de dar vigencia pero no borra la fila")
    void revocarNoBorra() {
        Consent consent = consentService.record(atleta,
                peticion(ConsentType.COMMUNICATIONS, true, ConsentEvidenceType.EMAIL), null);

        consentService.revoke(consent.getId());

        assertThat(consentService.hasActiveConsent(atleta, ConsentType.COMMUNICATIONS)).isFalse();
        assertThat(consentRepository.findById(consent.getId()))
                .as("la prueba de que se otorgo tiene que sobrevivir a la revocacion")
                .get()
                .satisfies(c -> {
                    assertThat(c.getRevokedAt()).isNotNull();
                    assertThat(c.isGranted()).isTrue();
                });
    }

    @Test
    @DisplayName("no se revoca dos veces")
    void revocarDosVecesFalla() {
        Consent consent = consentService.record(atleta,
                peticion(ConsentType.HEALTH_DATA, true, ConsentEvidenceType.PAPER_FORM), null);
        consentService.revoke(consent.getId());

        assertThatThrownBy(() -> consentService.revoke(consent.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------
    //  3. Quien puede consentir
    // ----------------------------------------------------------------

    @Test
    @DisplayName("un tutor del club sin vinculo con el atleta no puede consentir por el")
    void tutorSinVinculoNoConsiente() {
        ConsentRequest request = peticion(ConsentType.DATA_PROCESSING, true, ConsentEvidenceType.PAPER_FORM);
        request.setGuardianId(tutorSinVinculo);

        assertThatThrownBy(() -> consentService.record(atleta, request, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------
    //  4. Minimizacion de la IP
    // ----------------------------------------------------------------

    @Test
    @DisplayName("la IP solo se conserva cuando la evidencia es el formulario en linea")
    void laIpSoloSeGuardaEnElFormularioEnLinea() {
        Consent enPapel = consentService.record(atleta,
                peticion(ConsentType.DATA_PROCESSING, true, ConsentEvidenceType.PAPER_FORM), "10.0.0.1");
        Consent enLinea = consentService.record(atleta,
                peticion(ConsentType.DATA_PROCESSING, true, ConsentEvidenceType.ONLINE_FORM), "10.0.0.1");

        assertThat(enPapel.getSourceIp())
                .as("en papel la IP no prueba nada y es dato personal: no se guarda")
                .isNull();
        assertThat(enLinea.getSourceIp()).isEqualTo("10.0.0.1");

        consentService.revoke(enPapel.getId());
        consentService.revoke(enLinea.getId());
    }

    // ----------------------------------------------------------------
    //  5. La edad se mide en la fecha de la decision
    // ----------------------------------------------------------------

    @Test
    @DisplayName("la edad de consentimiento se mide en la fecha de la firma, no en la de hoy")
    void laEdadSeMideEnLaFechaDeLaDecision() {
        LocalDate nacimiento = LocalDate.of(2010, 1, 1);

        assertThat(ConsentService.isUnderConsentAgeOn(nacimiento, LocalDate.of(2021, 6, 1)))
                .as("con 11 anos consiente su tutor")
                .isTrue();
        assertThat(ConsentService.isUnderConsentAgeOn(nacimiento, LocalDate.of(2026, 9, 7)))
                .as("con 16 anos consiente el mismo, y lo que firmo su tutor con 11 sigue valiendo")
                .isFalse();
    }

    @Test
    @DisplayName("el dia que cumple 14 ya consiente el, no su tutor")
    void elDiaDelCumpleanosYaConsienteEl() {
        LocalDate nacimiento = LocalDate.of(2010, 1, 1);

        assertThat(ConsentService.isUnderConsentAgeOn(nacimiento, LocalDate.of(2023, 12, 31)))
                .as("la vispera todavia es menor de 14")
                .isTrue();
        assertThat(ConsentService.isUnderConsentAgeOn(nacimiento, LocalDate.of(2024, 1, 1)))
                .as("el mismo dia que los cumple ya no lo es")
                .isFalse();
    }

    @Test
    @DisplayName("un atleta de 11 anos necesita hoy el consentimiento de su tutor")
    void elMenorNecesitaTutor() {
        assertThat(consentService.requiresGuardianConsent(atleta)).isTrue();
    }
}
