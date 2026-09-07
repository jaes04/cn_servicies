package es.jaes.cn_servicies.medical_certificate;

import es.jaes.cn_servicies.athlete.AthleteService;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tarea S.1.b, opcion A: el certificado medico como metadatos.
 *
 * <p>Lo que hay que demostrar es que <b>el estado se calcula</b> y no se guarda
 * —si se almacenara, un certificado caducaria sin que nadie se enterase— y que
 * cuando hay varios manda el que mas lejos caduca, no el ultimo registrado.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MedicalCertificateServiceTest {

    private static final UUID CLUB = UUID.fromString("ffffffff-0000-0000-0000-00000000000f");
    private static final String SLUG = "club-certificados-it";
    private static final String ADMIN = "admin_cert_it";

    @Autowired private MedicalCertificateService certificateService;
    @Autowired private AthleteService athleteService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private UUID atleta;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Certificados IT', ?, true, now())", CLUB, SLUG);
        crearAdmin();
        atleta = crearAtleta();
    }

    @BeforeEach
    void contexto() {
        modoPublico();
        jdbc.update("DELETE FROM medical_certificates WHERE club_id = ?", CLUB);
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
        jdbc.update("DELETE FROM medical_certificates WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM athletes WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN"
                + " (SELECT id FROM users WHERE club_id = ?)", CLUB);
        jdbc.update("DELETE FROM users WHERE club_id = ?", CLUB);
        jdbc.update("DELETE FROM clubs WHERE id = ?", CLUB);
    }

    private void crearAdmin() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users"
                        + " (id, club_id, username, email, password_hash, blocked, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, false, now(), now())",
                id, CLUB, ADMIN, ADMIN + "@it.local", passwordEncoder.encode("clave-de-prueba-it"));
        jdbc.update("INSERT INTO user_roles (user_id, role_id)"
                + " SELECT ?, id FROM roles WHERE name = 'ROLE_ADMIN'", id);
    }

    private UUID crearAtleta() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO athletes"
                        + " (id, club_id, first_name, last_name, birth_date, dni, gender_id, created_at, updated_at)"
                        + " SELECT ?, ?, 'Atleta', 'Certificado', DATE '2012-02-02', '44444441A', g.id, now(), now()"
                        + " FROM genders g WHERE g.name = 'MALE'",
                id, CLUB);
        return id;
    }

    private MedicalCertificate registrar(LocalDate emision, LocalDate caducidad) {
        MedicalCertificateRequest request = new MedicalCertificateRequest();
        request.setIssuedOn(emision);
        request.setExpiresOn(caducidad);
        return certificateService.register(athleteService.findOrThrow(atleta), request, ADMIN);
    }

    // ----------------------------------------------------------------
    //  1. El estado sale de las fechas
    // ----------------------------------------------------------------

    @Test
    @DisplayName("sin certificado el estado es MISSING, no un error")
    void sinCertificadoEsMissing() {
        assertThat(certificateService.statusForAthlete(atleta))
                .isEqualTo(MedicalCertificateStatus.MISSING);
        assertThat(certificateService.hasValidCertificate(atleta)).isFalse();
    }

    @Test
    @DisplayName("uno con un año por delante está vigente")
    void unoConMargenEstaVigente() {
        registrar(LocalDate.now().minusDays(1), LocalDate.now().plusYears(1));

        assertThat(certificateService.statusForAthlete(atleta))
                .isEqualTo(MedicalCertificateStatus.VALID);
        assertThat(certificateService.hasValidCertificate(atleta)).isTrue();
    }

    @Test
    @DisplayName("a diez días de caducar avisa, y todavía cubre")
    void aDiezDiasAvisa() {
        registrar(LocalDate.now().minusYears(1), LocalDate.now().plusDays(10));

        assertThat(certificateService.statusForAthlete(atleta))
                .isEqualTo(MedicalCertificateStatus.EXPIRING_SOON);
        assertThat(certificateService.hasValidCertificate(atleta))
                .as("a punto de caducar todavía es válido: el aviso no retira la cobertura")
                .isTrue();
    }

    @Test
    @DisplayName("uno caducado ayer ya no cubre, sin que nadie lo haya tocado")
    void elCaducadoDejaDeCubrirSolo() {
        registrar(LocalDate.now().minusYears(1), LocalDate.now().minusDays(1));

        assertThat(certificateService.statusForAthlete(atleta))
                .isEqualTo(MedicalCertificateStatus.EXPIRED);
        assertThat(certificateService.hasValidCertificate(atleta)).isFalse();
    }

    @Test
    @DisplayName("el día de la caducidad todavía cubre")
    void elDiaDeLaCaducidadCubre() {
        registrar(LocalDate.now().minusYears(1), LocalDate.now());

        assertThat(certificateService.statusForAthlete(atleta))
                .isEqualTo(MedicalCertificateStatus.EXPIRING_SOON);
    }

    // ----------------------------------------------------------------
    //  2. Cuál manda cuando hay varios
    // ----------------------------------------------------------------

    @Test
    @DisplayName("manda el que más lejos caduca, no el último registrado")
    void mandaElQueMasLejosCaduca() {
        // El del año que viene primero, y después se teclea el viejo: es lo que
        // pasa cuando alguien pone al día el archivo. El estado no puede
        // empeorar por eso.
        registrar(LocalDate.now().minusDays(2), LocalDate.now().plusMonths(11));
        registrar(LocalDate.now().minusYears(2), LocalDate.now().minusYears(1));

        assertThat(certificateService.statusForAthlete(atleta))
                .isEqualTo(MedicalCertificateStatus.VALID);
        assertThat(certificateService.historyForAthlete(atleta)).hasSize(2);
    }

    // ----------------------------------------------------------------
    //  3. Validación y avisos
    // ----------------------------------------------------------------

    @Test
    @DisplayName("una caducidad anterior a la emisión se rechaza")
    void caducidadAnteriorALaEmisionSeRechaza() {
        assertThatThrownBy(() -> registrar(LocalDate.now(), LocalDate.now().minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la lista de caducidades próximas recoge el que vence dentro de 30 días y no el de dentro de un año")
    void laListaDeCaducidadesProximas() {
        registrar(LocalDate.now().minusYears(1), LocalDate.now().plusDays(10));

        assertThat(certificateService.expiringWithin(30))
                .as("el que vence en 10 días entra")
                .hasSize(1);
        assertThat(certificateService.expiringWithin(5))
                .as("con la ventana en 5 días ya no entra")
                .isEmpty();
    }

    @Test
    @DisplayName("un certificado ya caducado no sale en las caducidades próximas: eso es otra lista")
    void elCaducadoNoEsUnAviso() {
        registrar(LocalDate.now().minusYears(1), LocalDate.now().minusDays(1));

        assertThat(certificateService.expiringWithin(30)).isEmpty();
    }

    // ----------------------------------------------------------------
    //  4. Minimización
    // ----------------------------------------------------------------

    @Test
    @DisplayName("la respuesta no expone nada clínico: solo fechas, estado y quién validó")
    void laRespuestaSoloLlevaMetadatos() {
        MedicalCertificate certificado =
                registrar(LocalDate.now().minusDays(1), LocalDate.now().plusYears(1));

        MedicalCertificateResponse response = certificateService.toResponse(certificado);

        assertThat(response.getStatus()).isEqualTo(MedicalCertificateStatus.VALID);
        assertThat(response.getValidatedBy()).isEqualTo(ADMIN);
        assertThat(response.getIssuedOn()).isNotNull();
        assertThat(response.getExpiresOn()).isNotNull();
    }
}
