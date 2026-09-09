package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.athlete.Athlete;
import es.jaes.cn_servicies.athlete.AthleteService;
import es.jaes.cn_servicies.training_group.TrainingGroup;
import es.jaes.cn_servicies.training_group.TrainingGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Informes de asistencia (tarea 2.4).
 *
 * <p><b>Que se divide entre que.</b> El denominador son las sesiones
 * <b>celebradas</b> —ni las canceladas ni las futuras: faltar a un entrenamiento
 * que no existio no es faltar— en las que el atleta <b>pertenecia al grupo ese
 * dia</b>. Y una sesion celebrada en la que nadie le marco nada cuenta como
 * falta, pero sale ademas como incidencia con su fecha para que se pueda
 * corregir.
 *
 * <p>Una sesion pasada a la que <b>no se paso lista en absoluto</b> es harina de
 * otro costal: no cuenta como falta de nadie. Contar catorce ausencias porque el
 * entrenador no abrio el movil no seria un dato, seria ruido en el historico de
 * catorce familias. Va aparte, en {@code sessionsWithoutRoster}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceReportService {

    private static final int UMBRAL_AUSENCIAS_POR_DEFECTO = 3;

    /** Ventana del aviso de pendientes cuando no se pide un rango. */
    private static final int DIAS_DE_PENDIENTES = 30;

    private final AttendanceReportRepository reportRepository;
    private final TrainingGroupService groupService;
    private final AthleteService athleteService;

    // ----------------------------------------------------------------
    //  Por atleta
    // ----------------------------------------------------------------

    /** Suma sus grupos: un nadador puede estar en natacion y en preparacion fisica. */
    public AthleteAttendanceResponse forAthlete(UUID athleteId, LocalDate from, LocalDate to) {
        Athlete athlete = athleteService.findOrThrow(athleteId);
        validarRango(from, to);

        // El nombre sale del atleta y no de las filas: si en el rango no hubo
        // ninguna sesion, no hay filas de las que sacarlo, y un informe vacio
        // tiene que decir igualmente de quien es.
        String nombre = athlete.getFirstName() + " " + athlete.getLastName();

        Acumulador acumulador = new Acumulador(nombre);
        leer(reportRepository.findAttendanceGridForAthlete(athleteId, from, to))
                .forEach(acumulador::sumar);
        return acumulador.aResponse(athleteId);
    }

    // ----------------------------------------------------------------
    //  Por grupo
    // ----------------------------------------------------------------

    public GroupAttendanceResponse forGroup(UUID groupId, LocalDate from, LocalDate to) {
        TrainingGroup group = groupService.findOrThrow(groupId);
        validarRango(from, to);

        List<Fila> filas = leer(reportRepository.findAttendanceGrid(groupId, from, to));

        Map<UUID, Acumulador> porAtleta = new LinkedHashMap<>();
        for (Fila fila : filas) {
            porAtleta.computeIfAbsent(fila.athleteId(), id -> new Acumulador(fila.athleteName()))
                    .sumar(fila);
        }

        List<AthleteAttendanceResponse> atletas = porAtleta.entrySet().stream()
                .map(e -> e.getValue().aResponse(e.getKey()))
                .toList();

        GroupAttendanceResponse response = new GroupAttendanceResponse();
        response.setGroupId(groupId);
        response.setGroupName(group.getName());
        response.setFrom(from);
        response.setTo(to);
        response.setSessions((int) filas.stream().map(Fila::sessionId).distinct().count());
        response.setAthletes(atletas);
        response.setAttendanceRate(mediaDelGrupo(atletas));
        response.setSessionsWithoutRoster(sesionesSinLista(groupId, from, to));
        return response;
    }

    /**
     * Media del grupo sobre el total de asistencias posibles, no promediando los
     * porcentajes de cada uno: quien solo pudo ir a dos sesiones no puede pesar
     * lo mismo que quien pudo ir a veinte.
     */
    private Double mediaDelGrupo(List<AthleteAttendanceResponse> atletas) {
        int posibles = atletas.stream().mapToInt(AthleteAttendanceResponse::getSessions).sum();
        if (posibles == 0) {
            return null;
        }
        int asistidas = atletas.stream()
                .mapToInt(a -> a.getPresent() + a.getLate())
                .sum();
        return redondear((double) asistidas / posibles);
    }

    private List<AttendanceIncidentResponse> sesionesSinLista(UUID groupId, LocalDate from,
                                                             LocalDate to) {
        return reportRepository.findPastWithoutRoster(groupId, from, to, LocalDate.now()).stream()
                .map(sesion -> {
                    AttendanceIncidentResponse incidencia = new AttendanceIncidentResponse();
                    incidencia.setSessionId(sesion.getId());
                    incidencia.setDate(sesion.getDate());
                    return incidencia;
                })
                .toList();
    }

    // ----------------------------------------------------------------
    //  Pendientes de registrar
    // ----------------------------------------------------------------

    /**
     * Lo que le falta al club por registrar, para que el entrenador se entere.
     *
     * <p>Se consulta al entrar y se pinta como aviso. Es la alternativa honesta a
     * un correo automatico que nadie abre o a un {@code cron} que escribe en un
     * log que nadie lee: el aviso aparece donde la persona ya esta mirando.
     *
     * <p>Sin rango se miran los ultimos {@value #DIAS_DE_PENDIENTES} dias. Un
     * aviso que arrastrara tres temporadas de olvidos no seria accionable, seria
     * un numero grande al que se deja de hacer caso.
     */
    public PendingAttendanceResponse pending(LocalDate from, LocalDate to) {
        LocalDate hasta = to != null ? to : LocalDate.now();
        LocalDate desde = from != null ? from : hasta.minusDays(DIAS_DE_PENDIENTES);
        validarRango(desde, hasta);

        List<PendingSessionResponse> sinLista = reportRepository
                .findPastWithoutRosterInClub(desde, hasta, LocalDate.now()).stream()
                .map(sesion -> {
                    PendingSessionResponse pendiente = new PendingSessionResponse();
                    pendiente.setSessionId(sesion.getId());
                    pendiente.setDate(sesion.getDate());
                    pendiente.setGroupId(sesion.getTrainingGroup().getId());
                    pendiente.setGroupName(sesion.getTrainingGroup().getName());
                    return pendiente;
                })
                .toList();

        List<PendingSessionResponse> aMedias = new ArrayList<>();
        for (Object[] fila : reportRepository.findIncompleteRosters(desde, hasta)) {
            PendingSessionResponse pendiente = new PendingSessionResponse();
            pendiente.setSessionId((UUID) fila[0]);
            pendiente.setDate(((Date) fila[1]).toLocalDate());
            pendiente.setGroupId((UUID) fila[2]);
            pendiente.setGroupName((String) fila[3]);
            pendiente.setUnrecorded(((Number) fila[4]).intValue());
            aMedias.add(pendiente);
        }

        PendingAttendanceResponse response = new PendingAttendanceResponse();
        response.setFrom(desde);
        response.setTo(hasta);
        response.setSessionsWithoutRoster(sinLista);
        response.setIncompleteRosters(aMedias);
        response.setTotal(sinLista.size() + aMedias.size());
        return response;
    }

    // ----------------------------------------------------------------
    //  Ausencias consecutivas
    // ----------------------------------------------------------------

    /**
     * Atletas del grupo que llevan {@code umbral} sesiones seguidas sin
     * aparecer, contando desde la mas reciente hacia atras.
     *
     * <p>Las no registradas rompen la racha igual que una falta: si el criterio
     * es que cuentan como ausencia, tienen que contar tambien aqui, o el
     * indicador diria que un nadador viene cuando lo unico que pasa es que nadie
     * lo apunta.
     */
    public List<AttendanceGapResponse> gaps(UUID groupId, LocalDate from, LocalDate to,
                                            Integer umbral) {
        groupService.findOrThrow(groupId);
        validarRango(from, to);

        int minimo = umbral == null ? UMBRAL_AUSENCIAS_POR_DEFECTO : umbral;
        if (minimo < 1) {
            throw new IllegalArgumentException("El umbral tiene que ser al menos 1");
        }

        Map<UUID, List<Fila>> porAtleta = new LinkedHashMap<>();
        for (Fila fila : leer(reportRepository.findAttendanceGrid(groupId, from, to))) {
            porAtleta.computeIfAbsent(fila.athleteId(), id -> new ArrayList<>()).add(fila);
        }

        List<AttendanceGapResponse> resultado = new ArrayList<>();
        porAtleta.forEach((athleteId, filas) -> {
            int racha = 0;
            LocalDate ultimaAsistida = null;

            // Las filas vienen ordenadas por fecha: se recorren del final hacia
            // atras hasta encontrar la primera a la que si vino.
            for (int i = filas.size() - 1; i >= 0; i--) {
                Fila fila = filas.get(i);
                if (asistio(fila.status())) {
                    ultimaAsistida = fila.date();
                    break;
                }
                racha++;
            }

            if (racha >= minimo) {
                AttendanceGapResponse gap = new AttendanceGapResponse();
                gap.setAthleteId(athleteId);
                gap.setAthleteName(filas.get(0).athleteName());
                gap.setConsecutiveAbsences(racha);
                gap.setLastAttendedOn(ultimaAsistida);
                resultado.add(gap);
            }
        });
        return resultado;
    }

    // ----------------------------------------------------------------
    //  Interioridades
    // ----------------------------------------------------------------

    /** Una celda de la rejilla: un atleta en una sesion, con su estado o sin el. */
    private record Fila(UUID athleteId, String athleteName, UUID sessionId,
                        LocalDate date, AttendanceStatus status) {}

    private List<Fila> leer(List<Object[]> crudas) {
        List<Fila> filas = new ArrayList<>(crudas.size());
        for (Object[] c : crudas) {
            filas.add(new Fila(
                    (UUID) c[0],
                    c[1] + " " + c[2],
                    (UUID) c[3],
                    ((Date) c[4]).toLocalDate(),
                    c[5] == null ? null : AttendanceStatus.valueOf((String) c[5])));
        }
        return filas;
    }

    private boolean asistio(AttendanceStatus status) {
        return status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE;
    }

    private static Double redondear(double valor) {
        return Math.round(valor * 10000d) / 10000d;
    }

    private void validarRango(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Hacen falta las dos fechas del rango");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("El fin del rango es anterior al inicio");
        }
    }

    /** Va sumando las celdas de un atleta. */
    private final class Acumulador {

        private final String nombre;
        private final List<AttendanceIncidentResponse> incidencias = new ArrayList<>();
        private int sesiones;
        private int present;
        private int late;
        private int absent;
        private int excused;
        private int sinRegistrar;

        private Acumulador(String nombre) {
            this.nombre = nombre;
        }

        private void sumar(Fila fila) {
            sesiones++;
            if (fila.status() == null) {
                sinRegistrar++;
                AttendanceIncidentResponse incidencia = new AttendanceIncidentResponse();
                incidencia.setSessionId(fila.sessionId());
                incidencia.setDate(fila.date());
                incidencia.setAthleteId(fila.athleteId());
                incidencia.setAthleteName(fila.athleteName());
                incidencias.add(incidencia);
                return;
            }
            switch (fila.status()) {
                case PRESENT -> present++;
                case LATE -> late++;
                case ABSENT -> absent++;
                case EXCUSED -> excused++;
            }
        }

        private AthleteAttendanceResponse aResponse(UUID athleteId) {
            AthleteAttendanceResponse response = new AthleteAttendanceResponse();
            response.setAthleteId(athleteId);
            response.setAthleteName(nombre);
            response.setSessions(sesiones);
            response.setPresent(present);
            response.setLate(late);
            response.setAbsent(absent);
            response.setExcused(excused);
            response.setUnrecorded(sinRegistrar);
            response.setAttendanceRate(
                    sesiones == 0 ? null : redondear((double) (present + late) / sesiones));
            response.setIncidents(incidencias);
            return response;
        }
    }
}
