package es.jaes.cn_servicies.training_group;

import es.jaes.cn_servicies.athlete.Athlete;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pertenencia de un atleta a un grupo, con sus fechas. Es el historico: sobre
 * esta tabla se apoya toda la Fase 2.
 *
 * <p><b>Nunca se borra una fila.</b> Dar de baja es escribir {@link #leftOn}.
 * Borrarla haria imposible responder "quienes estaban en este grupo el 12 de
 * marzo", que es la consulta que sostiene la asistencia.
 *
 * <p><b>{@code leftOn} es el ultimo dia de pertenencia, incluido.</b> Un atleta
 * con baja el 30 de junio SI cuenta como miembro el 30 de junio. Fijar este
 * criterio importa: la Fase 2 lo hereda entero, y el error de un dia se
 * multiplica por cada sesion.
 *
 * <p>No lleva {@code club_id}: es tabla hija y llega a su club por cualquiera
 * de sus dos padres, ambos filtrados. Es la regla de la 0.2, y el roadmap ya
 * nombraba esta tabla como una de ellas.
 *
 * <p>El campo se llama {@code trainingGroup} y no {@code group} porque
 * {@code group} es palabra reservada tambien en HQL: {@code m.group} rompe
 * cualquier consulta que lo use.
 */
@Entity
@Table(name = "athlete_groups")
@Data
@NoArgsConstructor
public class AthleteGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private TrainingGroup trainingGroup;

    @Column(nullable = false)
    private LocalDate joinedOn;

    /** Nulo mientras la pertenencia siga abierta. Ultimo dia de pertenencia, incluido. */
    private LocalDate leftOn;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private LeaveReason leaveReason;

    @CreationTimestamp
    private LocalDateTime createdAt;

    /** Abierta: sin fecha de baja. */
    public boolean isOpen() {
        return leftOn == null;
    }

    /** Si la pertenencia cubre esa fecha, con los dos extremos incluidos. */
    public boolean coversOn(LocalDate date) {
        return !joinedOn.isAfter(date) && (leftOn == null || !leftOn.isBefore(date));
    }
}
