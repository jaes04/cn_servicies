package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.training_group.GroupSchedule;
import es.jaes.cn_servicies.training_group.TrainingGroup;
import es.jaes.cn_servicies.training_group.TrainingModality;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Un entrenamiento concreto: el martes 14 de octubre, de 18:00 a 19:00.
 *
 * <p>Es lo que el horario de la 2.1 <b>materializa</b>. La diferencia entre los
 * dos es que el horario dice lo que se hace todos los martes y la sesion dice lo
 * que paso el martes 14, que puede ser que se cancelara.
 *
 * <p><b>Lleva {@code club_id} y policy propia aunque sea tabla hija</b>, y es
 * una excepcion deliberada a la regla, como {@code consents}. El motivo es
 * concreto: es la primera tabla hija cuyo id viaja solo en la API. La 2.3
 * expone {@code /api/sessions/{id}/roster} para el movil, y un id suelto es un
 * {@code findById} por clave primaria, que es justo donde el filtro de Hibernate
 * no llega y RLS si. La alternativa era anidarlo todo bajo el grupo y obligar al
 * movil a una ruta de cuatro segmentos para pasar lista.
 *
 * <p><b>La hora y la modalidad se copian del horario, no se leen de el.</b> Si
 * en marzo el grupo se mueve de las 18:00 a las 19:00, las sesiones de febrero
 * tienen que seguir diciendo 18:00: es la hora a la que se entreno de verdad.
 * Leerlas por la relacion reescribiria el pasado cada vez que alguien corrige un
 * horario.
 *
 * <p><b>Sin zona horaria, a proposito.</b> {@link LocalDate} y {@link LocalTime}
 * sueltos: un entrenamiento a las 18:00 es a las 18:00 tambien el fin de semana
 * en que cambia la hora. Meter un instante con zona convertiria el cambio de
 * hora en una sesion desplazada.
 *
 * <p>No se borra: una sesion que no se dio se <b>cancela</b> con su motivo. La
 * fila es lo que explica el hueco en el calendario, y en la 2.3 de ella colgara
 * la asistencia.
 */
/*
 * El unico (schedule_id, session_date) NO se declara aqui, sino en schema.sql
 * como `uk_training_sessions_horario_fecha`. Declararlo en los dos sitios crea
 * dos restricciones equivalentes, y la que genera Hibernate se llama
 * `uk5a01qd9seiex615ick5rt6obp`: un nombre distinto en cada base, que no se
 * puede nombrar en una migracion ni en un ON CONFLICT.
 */
@Entity
@Table(name = "training_sessions")
@Data
@NoArgsConstructor
@Filter(name = Club.CLUB_FILTER, condition = Club.CLUB_FILTER_CONDITION)
public class TrainingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private TrainingGroup trainingGroup;

    /**
     * De que horario salio. <b>Nulo en las sesiones puntuales</b> —una
     * competicion, un entrenamiento extra—, que no salen de ningun horario.
     *
     * <p>Ese nulo es tambien lo que hace que el unico {@code (schedule_id,
     * session_date)} no estorbe: en Postgres dos nulos no son iguales, asi que
     * el indice bloquea el duplicado del generador y deja poner varias sesiones
     * sueltas el mismo dia.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private GroupSchedule schedule;

    /** {@code date} es palabra reservada en SQL; la columna es {@code session_date}. */
    @Column(name = "session_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrainingModality modality;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status = SessionStatus.SCHEDULED;

    /** Solo con {@code status = CANCELLED}, y entonces obligatorio. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CancellationReason cancellationReason;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public boolean isCancelled() {
        return status == SessionStatus.CANCELLED;
    }

    /** Sin horario detras: creada a mano y no por el generador. */
    public boolean isOneOff() {
        return schedule == null;
    }
}
