package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.athlete.Athlete;
import es.jaes.cn_servicies.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Que hizo un atleta en una sesion: vino, no vino, llego tarde.
 *
 * <p><b>No lleva {@code club_id}</b>, a diferencia de {@link TrainingSession}, y
 * la diferencia esta razonada: la sesion la lleva porque su id viaja solo en la
 * API; el de la asistencia no. Aqui se entra siempre por
 * {@code /api/sessions/&#123;id&#125;/...}, asi que se llega al club por la sesion —que
 * si tiene policy— igual que los horarios llegan por su grupo. Es el patron de
 * {@code group_schedules}, no el de {@code training_sessions}.
 *
 * <p><b>Sin campo de observaciones.</b> El roadmap lo pedia y se quito: un texto
 * libre en el registro de un menor, visible para todo el personal tecnico, acaba
 * guardando datos de salud sin base legal. Ver {@link AttendanceStatus} y
 * {@code docs/rgpd.md} §9.
 *
 * <p>{@link #registeredBy} y {@link #registeredAt} son trazabilidad, no adorno:
 * cuando dentro de tres meses alguien reclame una falta, la pregunta va a ser
 * quien la puso y cuando.
 */
@Entity
@Table(name = "attendance")
@Data
@NoArgsConstructor
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * <b>{@code NO_CONSTRAINT} no significa que no haya clave foranea</b>:
     * significa que no la crea Hibernate. La crea {@code schema.sql} con un
     * {@code ALTER TABLE} suelto, que es la unica forma de que lleve su
     * {@code ON DELETE CASCADE} — lo que va dentro de un {@code CREATE TABLE} en
     * ese archivo no llega a la base.
     *
     * <p>Si se dejara a Hibernate, habria <b>dos</b> claves foraneas sobre la
     * misma columna: la suya sin accion de borrado y la nuestra con cascada. Y la
     * que manda es la mas restrictiva, asi que la cascada no serviria de nada y
     * borrar una sesion con lista pasada seguiria fallando.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private TrainingSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Athlete athlete;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    /**
     * Quien paso lista. Nulo si el usuario se dio de baja despues: el registro
     * de asistencia tiene que sobrevivir a que su autor deje el club.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registered_by_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User registeredBy;

    /**
     * Cuando se registro por ultima vez. No es {@code @CreationTimestamp}: al
     * corregir una falta, lo que interesa es cuando se corrigio.
     */
    @Column(nullable = false)
    private LocalDateTime registeredAt;

    /** Vino, aunque fuera tarde. Lo que cuenta en el porcentaje de asistencia. */
    public boolean isAttended() {
        return status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE;
    }
}
