package es.jaes.cn_servicies.tenant;

import es.jaes.cn_servicies.athlete.Athlete;
import es.jaes.cn_servicies.athlete.AthleteRepository;
import es.jaes.cn_servicies.guardian.Consent;
import es.jaes.cn_servicies.guardian.ConsentRepository;
import es.jaes.cn_servicies.guardian.Guardian;
import es.jaes.cn_servicies.medical_certificate.MedicalCertificate;
import es.jaes.cn_servicies.medical_certificate.MedicalCertificateRepository;
import es.jaes.cn_servicies.season.Season;
import es.jaes.cn_servicies.season.SeasonRepository;
import es.jaes.cn_servicies.training_group.TrainingGroup;
import es.jaes.cn_servicies.training_group.TrainingGroupRepository;
import es.jaes.cn_servicies.training_session.ClubClosure;
import es.jaes.cn_servicies.training_session.ClubClosureRepository;
import es.jaes.cn_servicies.training_session.TrainingSession;
import es.jaes.cn_servicies.training_session.TrainingSessionRepository;
import es.jaes.cn_servicies.guardian.GuardianRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Criterio de aceptacion de la Fase 0: que un club no vea nada de otro.
 *
 * <p><b>Necesita un PostgreSQL de verdad.</b> Row Level Security no se puede
 * simular con una base en memoria, asi que estos tests corren contra la base
 * configurada en el entorno.
 *
 * <p><b>Y tiene que conectarse con el rol de la aplicacion, no con un
 * superusuario.</b> Postgres deja que los superusuarios se salten las policies:
 * ejecutados como {@code postgres}, la mitad de estos tests pasarian en verde
 * sin demostrar nada, que es peor que no tenerlos. Por eso lo primero que se
 * comprueba es precisamente eso.
 *
 * <p>Los datos los crea el propio test, en dos clubes suyos, y los borra al
 * terminar: no depende de lo que haya en la base ni de conteos absolutos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantIsolationTest {

    private static final UUID CLUB_A = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID CLUB_B = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000000b");

    private static final String ADMIN_A = "admin_it_a";
    private static final String ADMIN_B = "admin_it_b";
    private static final String CLAVE = "clave-de-prueba-it";

    private static final String DNI_A = "88888881A";
    private static final String DNI_B = "88888882B";

    private static final String DNI_TUTOR_A = "88888883C";
    private static final String DNI_TUTOR_B = "88888884D";

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AthleteRepository athleteRepository;
    @Autowired private GuardianRepository guardianRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private MedicalCertificateRepository certificateRepository;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private TrainingGroupRepository groupRepository;
    @Autowired private TrainingSessionRepository sessionRepository;
    @Autowired private ClubClosureRepository closureRepository;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private UUID atletaA;
    private UUID atletaB;
    private UUID tutorA;
    private UUID tutorB;
    private UUID consentimientoA;
    private UUID consentimientoB;
    private UUID certificadoA;
    private UUID certificadoB;
    private UUID temporadaA;
    private UUID temporadaB;
    private UUID grupoA;
    private UUID grupoB;
    private UUID sesionA;
    private UUID sesionB;
    private UUID cierreA;
    private UUID cierreB;

    // ----------------------------------------------------------------
    //  Datos de prueba
    // ----------------------------------------------------------------

    @BeforeAll
    void crearDatos() {
        modoPublico();
        borrarDatos();

        crearClub(CLUB_A, "Club IT A", "club-it-a");
        crearClub(CLUB_B, "Club IT B", "club-it-b");
        crearAdmin(CLUB_A, ADMIN_A);
        crearAdmin(CLUB_B, ADMIN_B);
        atletaA = crearAtleta(CLUB_A, "AtletaDeA", DNI_A);
        atletaB = crearAtleta(CLUB_B, "AtletaDeB", DNI_B);
        tutorA = crearTutor(CLUB_A, "TutorDeA", DNI_TUTOR_A);
        tutorB = crearTutor(CLUB_B, "TutorDeB", DNI_TUTOR_B);
        consentimientoA = crearConsentimiento(CLUB_A, atletaA, tutorA);
        consentimientoB = crearConsentimiento(CLUB_B, atletaB, tutorB);
        certificadoA = crearCertificado(CLUB_A, atletaA);
        certificadoB = crearCertificado(CLUB_B, atletaB);
        temporadaA = crearTemporada(CLUB_A, "Temporada de A");
        temporadaB = crearTemporada(CLUB_B, "Temporada de B");
        grupoA = crearGrupo(CLUB_A, temporadaA, "Grupo de A");
        grupoB = crearGrupo(CLUB_B, temporadaB, "Grupo de B");
        sesionA = crearSesion(CLUB_A, grupoA);
        sesionB = crearSesion(CLUB_B, grupoB);
        cierreA = crearCierre(CLUB_A);
        cierreB = crearCierre(CLUB_B);
    }

    @AfterAll
    void borrarDatosAlTerminar() {
        modoPublico();
        borrarDatos();
        TenantContext.clear();
    }

    /**
     * Los datos de prueba se crean desde fuera de una peticion, asi que no pasan
     * por el aspecto que fija el club. Sin esto, las policies rechazarian cada
     * insercion.
     */
    private void modoPublico() {
        jdbc.queryForObject("SELECT set_config('app.club_id', 'public', false)", String.class);
    }

    private void borrarDatos() {
        // Antes que users: el certificado apunta a quien lo valido, y esa clave
        // foranea no es en cascada a proposito.
        jdbc.update("DELETE FROM club_closures WHERE club_id IN (?, ?)", CLUB_A, CLUB_B);
        jdbc.update("DELETE FROM training_sessions WHERE club_id IN (?, ?)", CLUB_A, CLUB_B);
        jdbc.update("DELETE FROM training_groups WHERE club_id IN (?, ?)", CLUB_A, CLUB_B);
        jdbc.update("DELETE FROM seasons WHERE club_id IN (?, ?)", CLUB_A, CLUB_B);
        jdbc.update("DELETE FROM medical_certificates WHERE club_id IN (?, ?)", CLUB_A, CLUB_B);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN"
                + " (SELECT id FROM users WHERE club_id IN (?, ?))", CLUB_A, CLUB_B);
        jdbc.update("DELETE FROM users WHERE club_id IN (?, ?)", CLUB_A, CLUB_B);
        // Antes que guardians y athletes: apunta a los dos.
        jdbc.update("DELETE FROM consents WHERE club_id IN (?, ?)", CLUB_A, CLUB_B);
        jdbc.update("DELETE FROM athlete_guardians WHERE guardian_id IN"
                + " (SELECT id FROM guardians WHERE club_id IN (?, ?))", CLUB_A, CLUB_B);
        // Antes que athletes: athlete_guardians cae por cascada desde los dos
        // lados, pero guardians no cuelga de nadie.
        jdbc.update("DELETE FROM guardians WHERE club_id IN (?, ?)", CLUB_A, CLUB_B);
        jdbc.update("DELETE FROM athletes WHERE club_id IN (?, ?)", CLUB_A, CLUB_B);
        jdbc.update("DELETE FROM clubs WHERE id IN (?, ?)", CLUB_A, CLUB_B);
    }

    private void crearClub(UUID id, String nombre, String slug) {
        jdbc.update("INSERT INTO clubs (id, name, slug, active, created_at)"
                + " VALUES (?, ?, ?, true, now())", id, nombre, slug);
    }

    private void crearAdmin(UUID clubId, String username) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users"
                        + " (id, club_id, username, email, password_hash, blocked, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, false, now(), now())",
                id, clubId, username, username + "@it.local", passwordEncoder.encode(CLAVE));
        jdbc.update("INSERT INTO user_roles (user_id, role_id)"
                + " SELECT ?, id FROM roles WHERE name = 'ROLE_ADMIN'", id);
    }

    private UUID crearAtleta(UUID clubId, String nombre, String dni) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO athletes"
                        + " (id, club_id, first_name, last_name, birth_date, dni, gender_id, created_at, updated_at)"
                        + " SELECT ?, ?, ?, 'Prueba', DATE '2010-01-01', ?, g.id, now(), now()"
                        + " FROM genders g WHERE g.name = 'MALE'",
                id, clubId, nombre, dni);
        return id;
    }

    private UUID crearTutor(UUID clubId, String nombre, String dni) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO guardians"
                        + " (id, club_id, first_name, last_name, dni, email, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'Prueba', ?, ?, now(), now())",
                id, clubId, nombre, dni, nombre.toLowerCase() + "@it.local");
        return id;
    }

    private UUID crearConsentimiento(UUID clubId, UUID atletaId, UUID tutorId) {
        jdbc.update("INSERT INTO athlete_guardians"
                        + " (id, athlete_id, guardian_id, relationship, created_at)"
                        + " VALUES (?, ?, ?, 'MOTHER', now())",
                UUID.randomUUID(), atletaId, tutorId);

        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO consents"
                        + " (id, club_id, athlete_id, guardian_id, type, granted,"
                        + "  decision_date, evidence_type, created_at)"
                        + " VALUES (?, ?, ?, ?, 'DATA_PROCESSING', true,"
                        + "  CURRENT_DATE, 'PAPER_FORM', now())",
                id, clubId, atletaId, tutorId);
        return id;
    }

    // ----------------------------------------------------------------
    //  Precondicion
    // ----------------------------------------------------------------

    @Test
    @DisplayName("la conexion NO es superusuario, o el resto de tests no probaria nada")
    void laConexionNoEsSuperusuario() {
        Boolean superusuario = jdbc.queryForObject(
                "SELECT rolsuper FROM pg_roles WHERE rolname = current_user", Boolean.class);

        assertThat(superusuario)
                .as("Los tests se estan ejecutando como superusuario, que se salta Row Level "
                        + "Security. Asi, los tests de aislamiento pasan sin demostrar nada. "
                        + "Configura PGUSER con el rol de la aplicacion (cn_app).")
                .isFalse();
    }

    // ----------------------------------------------------------------
    //  1. Un club no ve registros de otro
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el admin del club A no ve ni un atleta del club B")
    void unClubNoVeAtletasDeOtro() {
        String cuerpo = get("/api/athletes?size=100", iniciarSesion(ADMIN_A)).getBody();

        assertThat(cuerpo).contains("AtletaDeA");
        assertThat(cuerpo).doesNotContain("AtletaDeB");
        assertThat(cuerpo).doesNotContain(DNI_B);
    }

    @Test
    @DisplayName("el admin del club A no ve ni un usuario del club B")
    void unClubNoVeUsuariosDeOtro() {
        String cuerpo = get("/api/users?size=100", iniciarSesion(ADMIN_A)).getBody();

        assertThat(cuerpo).contains(ADMIN_A);
        assertThat(cuerpo).doesNotContain(ADMIN_B);
    }

    // ----------------------------------------------------------------
    //  2. Test negativo: sin filtro de Hibernate, RLS sigue tapando
    // ----------------------------------------------------------------

    @Test
    @DisplayName("findById se salta el filtro de Hibernate y aun asi no devuelve el atleta ajeno")
    @Transactional(readOnly = true)
    void findByIdNoDevuelveDatosDeOtroClub() {
        // Aqui NO se activa el filtro de Hibernate a proposito: findById carga
        // por clave primaria, donde los filtros no se aplican. Lo unico que
        // puede tapar la fila ajena es Row Level Security.
        //
        // Se fija la variable a mano porque el aspecto solo entra en los metodos
        // transaccionales de la aplicacion, no en los de un test.
        jdbc.queryForObject("SELECT set_config('app.club_id', ?, false)",
                String.class, CLUB_A.toString());

        Optional<Athlete> ajeno = athleteRepository.findById(atletaB);
        Optional<Athlete> propio = athleteRepository.findById(atletaA);

        assertThat(ajeno).as("el atleta del club B no puede verse desde el club A").isEmpty();
        assertThat(propio).as("el atleta del propio club si debe verse").isPresent();
    }

    /**
     * El mismo test que el de arriba, sobre la tabla que entra en la S.1. Es la
     * comprobacion de que la migracion S.1-guardians-rls.sql esta aplicada: sin
     * ella la tabla nace sin policy y este test se pone en rojo, que es
     * justamente lo que tiene que pasar.
     */
    @Test
    @DisplayName("findById tampoco devuelve el tutor de otro club")
    @Transactional(readOnly = true)
    void findByIdNoDevuelveTutoresDeOtroClub() {
        jdbc.queryForObject("SELECT set_config('app.club_id', ?, false)",
                String.class, CLUB_A.toString());

        Optional<Guardian> ajeno = guardianRepository.findById(tutorB);
        Optional<Guardian> propio = guardianRepository.findById(tutorA);

        assertThat(ajeno).as("el tutor del club B no puede verse desde el club A").isEmpty();
        assertThat(propio).as("el tutor del propio club si debe verse").isPresent();
    }

    private UUID crearCertificado(UUID clubId, UUID atletaId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO medical_certificates"
                        + " (id, club_id, athlete_id, issued_on, expires_on,"
                        + "  validated_by_id, validated_at, created_at)"
                        + " SELECT ?, ?, ?, CURRENT_DATE, CURRENT_DATE + 365, u.id, now(), now()"
                        + " FROM users u WHERE u.club_id = ? LIMIT 1",
                id, clubId, atletaId, clubId);
        return id;
    }

    private UUID crearTemporada(UUID clubId, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO seasons"
                        + " (id, club_id, name, start_date, end_date, active, created_at, updated_at)"
                        + " VALUES (?, ?, ?, DATE '2026-09-01', DATE '2027-08-31', true, now(), now())",
                id, clubId, nombre);
        return id;
    }

    private UUID crearGrupo(UUID clubId, UUID temporadaId, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO training_groups"
                        + " (id, club_id, season_id, name, category, level, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, 'ALEVIN', 'COMPETICION', now(), now())",
                id, clubId, temporadaId, nombre);
        return id;
    }

    private UUID crearCierre(UUID clubId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO club_closures"
                        + " (id, club_id, start_date, end_date, reason, modality,"
                        + "  created_at, updated_at)"
                        + " VALUES (?, ?, CURRENT_DATE, CURRENT_DATE, 'HOLIDAY', NULL,"
                        + "  now(), now())",
                id, clubId);
        return id;
    }

    /**
     * El calendario de excepciones se consulta por rango, y eso ya lo tapa el
     * filtro de Hibernate. Lo que solo tapa la policy es esto: cargarlo por su
     * id, que es como llega el borrado de un cierre.
     */
    @Test
    @DisplayName("findById tampoco devuelve el cierre de calendario de otro club")
    @Transactional(readOnly = true)
    void findByIdNoDevuelveCierresDeOtroClub() {
        jdbc.queryForObject("SELECT set_config('app.club_id', ?, false)",
                String.class, CLUB_A.toString());

        Optional<ClubClosure> ajeno = closureRepository.findById(cierreB);
        Optional<ClubClosure> propio = closureRepository.findById(cierreA);

        assertThat(ajeno.isPresent())
                .as("el cierre del club B no puede verse desde el club A")
                .isFalse();
        assertThat(propio.isPresent())
                .as("el del propio club sí debe verse")
                .isTrue();
    }

    private UUID crearSesion(UUID clubId, UUID grupoId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO training_sessions"
                        + " (id, club_id, group_id, schedule_id, session_date, start_time, end_time,"
                        + "  modality, status, created_at, updated_at)"
                        + " VALUES (?, ?, ?, NULL, CURRENT_DATE, '18:00', '19:00', 'SWIMMING',"
                        + "  'SCHEDULED', now(), now())",
                id, clubId, grupoId);
        return id;
    }

    /**
     * La sesion es la unica tabla hija que lleva {@code club_id} por este motivo
     * exacto: su id viaja solo en la API —{@code /api/sessions/{id}}, y en la 2.3
     * el {@code /roster} que consume el movil—, asi que se carga por clave
     * primaria y el filtro de Hibernate no interviene.
     *
     * <p>Este test tiene que hacerse <b>sobre el repositorio</b> y no sobre el
     * servicio. Pasando por el servicio pasa en verde aunque no haya policy
     * ninguna, porque al construir la respuesta se toca el nombre del grupo y lo
     * que salta es el proxy del grupo ajeno, que si esta tapado. El de aqui se
     * pone en rojo con {@code DISABLE ROW LEVEL SECURITY}, que es lo que tiene
     * que pasar.
     */
    @Test
    @DisplayName("findById tampoco devuelve la sesión de entrenamiento de otro club")
    @Transactional(readOnly = true)
    void findByIdNoDevuelveSesionesDeOtroClub() {
        jdbc.queryForObject("SELECT set_config('app.club_id', ?, false)",
                String.class, CLUB_A.toString());

        Optional<TrainingSession> ajena = sessionRepository.findById(sesionB);
        Optional<TrainingSession> propia = sessionRepository.findById(sesionA);

        assertThat(ajena.isPresent())
                .as("la sesión del club B no puede verse desde el club A")
                .isFalse();
        assertThat(propia.isPresent())
                .as("la sesión del propio club sí debe verse")
                .isTrue();
    }

    /**
     * El consentimiento es la anotacion que sostiene la licitud del tratamiento
     * de los datos de un menor. Si algo no puede cruzar clubes, es esto.
     */
    @Test
    @DisplayName("findById tampoco devuelve el consentimiento de otro club")
    @Transactional(readOnly = true)
    void findByIdNoDevuelveConsentimientosDeOtroClub() {
        jdbc.queryForObject("SELECT set_config('app.club_id', ?, false)",
                String.class, CLUB_A.toString());

        Optional<Consent> ajeno = consentRepository.findById(consentimientoB);
        Optional<Consent> propio = consentRepository.findById(consentimientoA);

        // Sobre el booleano y no sobre el Optional: al fallar, AssertJ pondria
        // la entidad en el mensaje, y el toString() de Lombok recorre las
        // relaciones LAZY. La del atleta ajeno esta tapada por RLS, asi que el
        // test moriria con EntityNotFoundException en vez de decir que fallo.
        assertThat(ajeno.isPresent())
                .as("el consentimiento del club B no puede verse desde el club A").isFalse();
        assertThat(propio.isPresent())
                .as("el consentimiento del propio club si debe verse").isTrue();
    }

    /**
     * Saber si un menor tiene certificado en plazo no cruza clubes, aunque la
     * tabla no guarde ningun dato clinico.
     */
    @Test
    @DisplayName("findById tampoco devuelve el certificado medico de otro club")
    @Transactional(readOnly = true)
    void findByIdNoDevuelveCertificadosDeOtroClub() {
        jdbc.queryForObject("SELECT set_config('app.club_id', ?, false)",
                String.class, CLUB_A.toString());

        Optional<MedicalCertificate> ajeno = certificateRepository.findById(certificadoB);
        Optional<MedicalCertificate> propio = certificateRepository.findById(certificadoA);

        assertThat(ajeno.isPresent())
                .as("el certificado del club B no puede verse desde el club A").isFalse();
        assertThat(propio.isPresent())
                .as("el certificado del propio club si debe verse").isTrue();
    }

    /**
     * De las temporadas colgaran los grupos y la asistencia: es la raiz del
     * historico deportivo y no puede cruzar clubes.
     *
     * <p>Los dos clubes tienen la suya ACTIVA a la vez, y eso es correcto: el
     * indice unico parcial es por club, no global.
     */
    @Test
    @DisplayName("findById tampoco devuelve la temporada de otro club")
    @Transactional(readOnly = true)
    void findByIdNoDevuelveTemporadasDeOtroClub() {
        jdbc.queryForObject("SELECT set_config('app.club_id', ?, false)",
                String.class, CLUB_A.toString());

        Optional<Season> ajena = seasonRepository.findById(temporadaB);
        Optional<Season> propia = seasonRepository.findById(temporadaA);

        assertThat(ajena.isPresent())
                .as("la temporada del club B no puede verse desde el club A").isFalse();
        assertThat(propia.isPresent())
                .as("la temporada del propio club si debe verse").isTrue();
    }

    /**
     * De los grupos colgara la pertenencia de los atletas y toda la asistencia:
     * es la raiz del dia a dia deportivo.
     */
    @Test
    @DisplayName("findById tampoco devuelve el grupo de otro club")
    @Transactional(readOnly = true)
    void findByIdNoDevuelveGruposDeOtroClub() {
        jdbc.queryForObject("SELECT set_config('app.club_id', ?, false)",
                String.class, CLUB_A.toString());

        Optional<TrainingGroup> ajeno = groupRepository.findById(grupoB);
        Optional<TrainingGroup> propio = groupRepository.findById(grupoA);

        assertThat(ajeno.isPresent())
                .as("el grupo del club B no puede verse desde el club A").isFalse();
        assertThat(propio.isPresent())
                .as("el grupo del propio club si debe verse").isTrue();
    }

    @Test
    @DisplayName("por HTTP, pedir por id un atleta de otro club devuelve 404")
    void porHttpElAtletaAjenoDa404() {
        String token = iniciarSesion(ADMIN_B);

        assertThat(get("/api/athletes/" + atletaA, token).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/athletes/" + atletaB, token).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ----------------------------------------------------------------
    //  3. Un token con otro club no da acceso
    // ----------------------------------------------------------------

    @Test
    @DisplayName("un token bien firmado pero con el club de otro no da acceso")
    void tokenConClubAjenoNoDaAcceso() {
        assertThat(get("/api/users/me", firmar(ADMIN_A, CLUB_B.toString())).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("un token bien firmado sin el claim club_id no da acceso")
    void tokenSinClubNoDaAcceso() {
        assertThat(get("/api/users/me", firmar(ADMIN_A, null)).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ----------------------------------------------------------------
    //  4. Regresion
    // ----------------------------------------------------------------

    @Test
    @DisplayName("el login sigue funcionando")
    void elLoginSigueFuncionando() {
        String token = iniciarSesion(ADMIN_A);

        assertThat(token).isNotBlank();
        assertThat(get("/api/users/me", token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("el blog publico sigue sirviendo sin autenticacion")
    void elBlogPublicoSigueSirviendo() {
        assertThat(rest.getForEntity("/api/posts/published", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("sin token no se entra")
    void sinTokenNoSeEntra() {
        assertThat(rest.getForEntity("/api/users/me", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ----------------------------------------------------------------
    //  Utilidades
    // ----------------------------------------------------------------

    private String iniciarSesion(String username) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        String cuerpo = "{\"username\":\"" + username + "\",\"password\":\"" + CLAVE + "\"}";

        ResponseEntity<String> respuesta = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(cuerpo, cabeceras), String.class);

        assertThat(respuesta.getStatusCode())
                .as("no se pudo iniciar sesion como %s", username)
                .isEqualTo(HttpStatus.OK);

        return respuesta.getBody().replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    private ResponseEntity<String> get(String ruta, String token) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(token);
        return rest.exchange(ruta, HttpMethod.GET, new HttpEntity<>(cabeceras), String.class);
    }

    /** Firma con el secreto real, para poder manipular los claims. */
    private String firmar(String username, String clubId) {
        var builder = Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000));
        if (clubId != null) {
            builder.claim("club_id", clubId);
        }
        return builder.signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret))).compact();
    }
}
