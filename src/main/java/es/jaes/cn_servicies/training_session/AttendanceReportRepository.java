package es.jaes.cn_servicies.training_session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Consultas de los informes de asistencia (tarea 2.4).
 *
 * <p>Cuelga de {@link TrainingSession} y no de {@link Attendance} porque la
 * pregunta que responde empieza por la sesion: "de las que se celebraron, quien
 * tenia que estar y que se registro de el". Un atleta sin fila de asistencia
 * tiene que aparecer igual —esa ausencia de fila <b>es</b> el dato— y eso solo
 * sale mirando desde las sesiones.
 */
public interface AttendanceReportRepository extends JpaRepository<TrainingSession, UUID> {

    /**
     * Una fila por (sesion celebrada, atleta que pertenecia al grupo ese dia),
     * con su estado o {@code null} si nadie lo marco.
     *
     * <p><b>Un solo viaje a la base</b> para todo el informe. Catorce atletas por
     * seis semanas resueltos atleta a atleta serian cientos de consultas para
     * pintar una tabla.
     *
     * <p>El {@code LEFT JOIN} es la pieza: conserva la fila aunque no haya
     * asistencia registrada, que es justo el caso que hay que contar como falta
     * y sacar como incidencia.
     *
     * <p>La pertenencia se comprueba <b>contra la fecha de la sesion</b>, con los
     * dos extremos incluidos, igual que {@code AthleteGroupRepository.findMembersOn}.
     * Es el criterio de la 1.3 y aqui se hereda entero: quien se incorporo en
     * enero no arrastra las faltas de octubre.
     *
     * <p>Nativa, y el aislamiento lo pone <b>RLS sobre {@code training_sessions}</b>:
     * los filtros de Hibernate no se aplican a las consultas nativas, pero las
     * policies de Postgres si. El grupo se valida ademas en el servicio.
     *
     * <p>Devuelve: {@code athlete_id, first_name, last_name, session_id,
     * session_date, status}.
     */
    @Query(value = """
            SELECT ag.athlete_id,
                   at.first_name,
                   at.last_name,
                   s.id            AS session_id,
                   s.session_date,
                   a.status
            FROM training_sessions s
            JOIN athlete_groups ag
              ON ag.group_id = s.group_id
             AND ag.joined_on <= s.session_date
             AND (ag.left_on IS NULL OR ag.left_on >= s.session_date)
            JOIN athletes at ON at.id = ag.athlete_id
            LEFT JOIN attendance a
              ON a.session_id = s.id AND a.athlete_id = ag.athlete_id
            WHERE s.group_id = :groupId
              AND s.session_date BETWEEN :from AND :to
              AND s.status = 'DONE'
            ORDER BY at.last_name, at.first_name, s.session_date
            """, nativeQuery = true)
    List<Object[]> findAttendanceGrid(UUID groupId, LocalDate from, LocalDate to);

    /**
     * Lo mismo para un solo atleta, mirando <b>todos sus grupos</b>: un nadador
     * puede estar en natacion y en preparacion fisica a la vez, y su asistencia
     * es la suma de las dos.
     */
    @Query(value = """
            SELECT ag.athlete_id,
                   at.first_name,
                   at.last_name,
                   s.id            AS session_id,
                   s.session_date,
                   a.status
            FROM training_sessions s
            JOIN athlete_groups ag
              ON ag.group_id = s.group_id
             AND ag.athlete_id = :athleteId
             AND ag.joined_on <= s.session_date
             AND (ag.left_on IS NULL OR ag.left_on >= s.session_date)
            JOIN athletes at ON at.id = ag.athlete_id
            LEFT JOIN attendance a
              ON a.session_id = s.id AND a.athlete_id = ag.athlete_id
            WHERE s.session_date BETWEEN :from AND :to
              AND s.status = 'DONE'
            ORDER BY s.session_date
            """, nativeQuery = true)
    List<Object[]> findAttendanceGridForAthlete(UUID athleteId, LocalDate from, LocalDate to);

    /**
     * Sesiones ya pasadas a las que <b>nadie paso lista</b>: siguen SCHEDULED
     * teniendo la fecha vencida.
     *
     * <p>Van aparte y no cuentan como falta de nadie. Contar catorce ausencias
     * porque el entrenador no abrio el movil no seria un dato, seria ruido que
     * ademas ensucia el historico de catorce familias.
     */
    @Query("SELECT s FROM TrainingSession s"
            + " WHERE s.trainingGroup.id = :groupId"
            + "   AND s.date BETWEEN :from AND :to"
            + "   AND s.date < :today"
            + "   AND s.status = es.jaes.cn_servicies.training_session.SessionStatus.SCHEDULED"
            + " ORDER BY s.date")
    List<TrainingSession> findPastWithoutRoster(UUID groupId, LocalDate from, LocalDate to,
                                                LocalDate today);

    /**
     * Lo mismo para <b>todo el club</b>, sin acotar a un grupo: es lo que
     * necesita el aviso de pendientes, porque un entrenador quiere saber que le
     * falta, no ir grupo por grupo preguntando.
     *
     * <p>Sin {@code club_id} en la condicion: es JPQL, asi que lo pone el filtro
     * de tenancy, y debajo esta la policy.
     */
    @Query("SELECT s FROM TrainingSession s"
            + " WHERE s.date BETWEEN :from AND :to"
            + "   AND s.date < :today"
            + "   AND s.status = es.jaes.cn_servicies.training_session.SessionStatus.SCHEDULED"
            + " ORDER BY s.date DESC")
    List<TrainingSession> findPastWithoutRosterInClub(LocalDate from, LocalDate to,
                                                      LocalDate today);

    /**
     * Sesiones celebradas del club a las que les quedaron atletas sin marcar,
     * con cuantos.
     *
     * <p>El {@code a.id IS NULL} despues del {@code LEFT JOIN} es el truco: deja
     * solo las combinaciones (sesion, atleta que tenia que estar) para las que no
     * hay fila de asistencia. Son las que ya estan contando como falta en los
     * informes, asi que son las que corre prisa corregir.
     *
     * <p>Nativa: el aislamiento lo pone RLS sobre {@code training_sessions} y
     * {@code training_groups}, no el filtro de Hibernate, que en las nativas no
     * interviene.
     *
     * <p>Devuelve: {@code session_id, session_date, group_id, group_name, sin_marcar}.
     */
    @Query(value = """
            SELECT s.id, s.session_date, g.id AS group_id, g.name AS group_name,
                   COUNT(*) AS sin_marcar
            FROM training_sessions s
            JOIN training_groups g ON g.id = s.group_id
            JOIN athlete_groups ag
              ON ag.group_id = s.group_id
             AND ag.joined_on <= s.session_date
             AND (ag.left_on IS NULL OR ag.left_on >= s.session_date)
            LEFT JOIN attendance a
              ON a.session_id = s.id AND a.athlete_id = ag.athlete_id
            WHERE s.session_date BETWEEN :from AND :to
              AND s.status = 'DONE'
              AND a.id IS NULL
            GROUP BY s.id, s.session_date, g.id, g.name
            ORDER BY s.session_date DESC
            """, nativeQuery = true)
    List<Object[]> findIncompleteRosters(LocalDate from, LocalDate to);
}
