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
 * El certificado medico como metadatos, <b>por temporada</b> desde el bloque 3b.
 *
 * <p>Lo que hay que demostrar es que <b>el estado se calcula</b> y no se guarda
 * —si se almacenara, un certificado caducaria sin que nadie se enterase— y que
 * <b>manda el de la temporada activa</b>, no el ultimo registrado.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MedicalCertificateServiceTest {

    private static final UUID CLUB = UUID.fromString("ffffffff-0000-0000-0000-00000000000f");
    private static final String SLUG = "club-certificados-it";
    private static final String ADMIN = "admin_cert_it";

    private static final LocalDate HOY = LocalDate.now();
    private static final LocalDate INICIO = HOY.minusMonths(3);
    private static final LocalDate FIN = HOY.plusMonths(8);

    @Autowired private MedicalCertificateService certificateService;
    @Autowired private AthleteService athleteService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private UUID atleta;
    private UUID temporadaActiva;
    private UUID temporadaPasada;

    @BeforeAll
    void inicio() {
        modoPublico();
        borrarTodo();
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, 'Club Certificados IT', ?, true, now())", CLUB, SLUG);
        crearAdmin();
        atleta = crearAtleta();
        temporadaActiva = crearTemporada("Temporada actual", INICIO, FIN, true);
        temporadaPasada = crearTemporada("Temporada pasada", INICIO.minusYears(1), INICIO.minusDays(1), false);
    }

    @BeforeEach
    void contexto() {
        modoPublico();
        jdbc.update("DELETE FROM medical_certificates WHERE club_id = ?", CLUB);
        // Algunos tests acercan el final de la temporada; cada uno empieza con la de siempre.
        jdbc.update("UPDATE seasons SET end_date = ? WHERE id = ?", FIN, temporadaActiva);
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
        jdbc.update("DELETE FROM seasons WHERE club_id = ?", CLUB);
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

    private UUID crearTemporada(String nombre, LocalDate inicio, LocalDate fin, boolean activa) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO seasons"
                        + " (id, club_id, name, start_date, end_date, active, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, now(), now())",
                id, CLUB, nombre, inicio, fin, activa);
        return id;
    }

    private MedicalCertificate registrar(LocalDate emision, UUID temporada) {
        MedicalCertificateRequest request = new MedicalCertificateRequest();
        request.setIssuedOn(emision);
        request.setSeasonId(temporada);
        return certificateService.register(athleteService.findOrThrow(atleta), request, ADMIN);
    }

    // ----------------------------------------------------------------
    //  1. El estado se mide contra la temporada activa
    // ----------------------------------------------------------------

    @Test
    @DisplayName("sin certificado el estado es MISSING, no un error")
    void sinCertificadoEsMissing() {
        assertThat(certificateService.statusForAthlete(atleta))
                .isEqualTo(MedicalCertificateStatus.MISSING);
        assertThat(certificateService.hasValidCertificate(atleta)).isFalse();
    }

    @Test
    @DisplayName("el de la temporada activa, con meses por delante, está vigente")
    void elDeLaTemporadaActivaEstaVigente() {
        registrar(HOY.minusDays(1), temporadaActiva);

        assertThat(certificateService.statusForAthlete(atleta))
                .isEqualTo(MedicalCertificateStatus.VALID);
        assertThat(certificateService.hasValidCertificate(atleta)).isTrue();
    }

    @Test
    @DisplayName("si la temporada acaba en diez días avisa, y todavía cubre")
    void aDiezDiasDelFinalAvisa() {
        jdbc.update("UPDATE seasons SET end_date = ? WHERE id = ?", HOY.plusDays(10), temporadaActiva);
        registrar(HOY.minusDays(1), temporadaActiva);

        assertThat(certificateService.statusForAthlete(atleta))
                .isEqualTo(MedicalCertificateStatus.EXPIRING_SOON);
        assertThat(certificateService.hasValidCertificate(atleta))
                .as("a punto de caducar todavía es válido: el aviso no retira la cobertura")
                .isTrue();
    }

    /**
     * Es lo que le sirve al club en septiembre: a quien trajo el del curso pasado
     * hay que pedirle que renueve, que no es lo mismo que pedirselo a quien nunca
     * trajo ninguno.
     */
    @Test
    @DisplayName("si solo tiene el del curso pasado, EXPIRED y no MISSING")
    void soloElDelCursoPasadoEsExpired() {
        registrar(INICIO.minusMonths(6), temporadaPasada);

        assertThat(certificateService.statusForAthlete(atleta))
                .isEqualTo(MedicalCertificateStatus.EXPIRED);
        assertThat(certificateService.hasValidCertificate(atleta)).isFalse();
    }

    @Test
    @DisplayName("manda el de la temporada activa, no el último registrado")
    void mandaElDeLaTemporadaActiva() {
        // El de este curso primero, y después se teclea el del pasado: es lo que
        // pasa cuando alguien pone al día el archivo. El estado no puede empeorar.
        registrar(HOY.minusDays(2), temporadaActiva);
        registrar(INICIO.minusMonths(6), temporadaPasada);

        assertThat(certificateService.statusForAthlete(atleta))
                .isEqualTo(MedicalCertificateStatus.VALID);
        assertThat(certificateService.historyForAthlete(atleta)).hasSize(2);
    }

    /**
     * Anotar en agosto el certificado del curso que viene no puede cubrir el que
     * acaba. Contar "el que mas lejos llega" diria VALID, y es justo lo que este
     * test impide: el curso actual sigue sin certificado.
     */
    @Test
    @DisplayName("el certificado del curso que viene no cubre el actual")
    void elDelCursoQueVieneNoCubreEste() {
        UUID siguiente = crearTemporada("Temporada siguiente", FIN.plusDays(1), FIN.plusYears(1), false);
        registrar(HOY, siguiente);

        assertThat(certificateService.statusForAthlete(atleta))
                .isEqualTo(MedicalCertificateStatus.EXPIRED);
    }

    // ----------------------------------------------------------------
    //  2. La caducidad es la de la temporada
    // ----------------------------------------------------------------

    @Test
    @DisplayName("la caducidad no se teclea: es el último día de la temporada")
    void laCaducidadEsElFinalDeLaTemporada() {
        MedicalCertificate certificado = registrar(HOY.minusDays(1), temporadaActiva);

        assertThat(certificado.getExpiresOn()).isEqualTo(FIN);
        assertThat(certificateService.toResponse(certificado).getExpiresOn()).isEqualTo(FIN);
    }

    @Test
    @DisplayName("una emisión posterior al final de la temporada se rechaza")
    void emisionPosteriorAlFinalSeRechaza() {
        assertThatThrownBy(() -> registrar(HOY, temporadaPasada))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temporada");
    }

    // ----------------------------------------------------------------
    //  3. Varios atletas de una vez (bloque 3c)
    // ----------------------------------------------------------------

    /**
     * El informe de documentacion pendiente pide el estado de todo el club de una
     * vez. Tiene que contestar lo mismo que la pregunta de uno en uno, o el aviso y
     * la ficha del atleta dirian cosas distintas.
     */
    @Test
    @DisplayName("el estado en bloque coincide con el de uno en uno, también para quien no tiene ninguno")
    void enBloqueComoDeUnoEnUno() {
        registrar(INICIO.minusMonths(6), temporadaPasada);
        UUID sinNinguno = UUID.randomUUID();

        java.util.Map<UUID, MedicalCertificateStatus> estados =
                certificateService.statusesForAthletes(java.util.List.of(atleta, sinNinguno));

        assertThat(estados.get(atleta))
                .isEqualTo(certificateService.statusForAthlete(atleta))
                .isEqualTo(MedicalCertificateStatus.EXPIRED);
        assertThat(estados.get(sinNinguno)).isEqualTo(MedicalCertificateStatus.MISSING);
    }

    // ----------------------------------------------------------------
    //  4. Minimización
    // ----------------------------------------------------------------

    @Test
    @DisplayName("la respuesta no expone nada clínico: fechas, temporada, estado y quién validó")
    void laRespuestaSoloLlevaMetadatos() {
        MedicalCertificate certificado = registrar(HOY.minusDays(1), temporadaActiva);

        MedicalCertificateResponse response = certificateService.toResponse(certificado);

        assertThat(response.getStatus()).isEqualTo(MedicalCertificateStatus.VALID);
        assertThat(response.getValidatedBy()).isEqualTo(ADMIN);
        assertThat(response.getSeasonId()).isEqualTo(temporadaActiva);
        assertThat(response.getSeasonName()).isEqualTo("Temporada actual");
        assertThat(response.getIssuedOn()).isNotNull();
    }
}
