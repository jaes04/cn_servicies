package es.jaes.cn_servicies.training_session;

import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.training_group.TrainingModality;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Un dia o un tramo en que no se entrena: un festivo, la piscina cerrada por
 * averia, la semana de Navidad.
 *
 * <p><b>No impide generar la sesion: hace que nazca cancelada.</b> Es la
 * decision de fondo de esta tarea. Si el 6 de diciembre simplemente no hubiera
 * nada en el calendario, nadie sabria si es que era festivo o si el job no llego
 * a pasar por esa semana; asi el calendario dice "6 de diciembre, cancelado:
 * festivo", que es lo que el club quiere ver. De paso hereda gratis la regla de
 * que una sesion cancelada no resucita al regenerar.
 *
 * <p><b>La modalidad es opcional y acota el cierre.</b> Sin ella el cierre
 * afecta a todo; con {@code SWIMMING} solo tumba lo que se hace en el agua. Es
 * lo que evita que cerrar la piscina cancele el entrenamiento del gimnasio, que
 * con la modalidad viviendo en el horario (2.1) seria un error visible desde el
 * primer festivo.
 *
 * <p>Es entidad raiz —el cierre es del club, no de un grupo— asi que lleva
 * {@code club_id} y policy propia, esta vez sin excepcion que justificar.
 *
 * <p>Vive en el paquete {@code training_session} y no en {@code club} porque su
 * unico efecto es sobre las sesiones: ponerlo aqui evita que los dos modulos se
 * llamen en circulo. Si algun dia un cierre afecta a algo mas, se mueve.
 */
@Entity
@Table(name = "club_closures")
@Data
@NoArgsConstructor
@Filter(name = Club.CLUB_FILTER, condition = Club.CLUB_FILTER_CONDITION)
public class ClubClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(nullable = false)
    private LocalDate startDate;

    /** Ultimo dia cerrado, incluido. Igual que {@code startDate} en un festivo suelto. */
    @Column(nullable = false)
    private LocalDate endDate;

    /**
     * Por que se cierra. Mismo enum que usa la cancelacion de una sesion suelta,
     * porque es exactamente el mismo dato: al cancelar por un cierre, este motivo
     * es el que acaba en la sesion.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CancellationReason reason;

    /** Nulo: afecta a todo. Con valor: solo a esa modalidad. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TrainingModality modality;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /** Si el cierre cubre esa fecha, con los dos extremos incluidos. */
    public boolean covers(LocalDate date) {
        return !startDate.isAfter(date) && !endDate.isBefore(date);
    }

    /** Un cierre sin modalidad afecta a todas. */
    public boolean appliesTo(TrainingModality otra) {
        return modality == null || modality == otra;
    }

    /** Si tumba una sesion de esa fecha y esa modalidad. */
    public boolean affects(LocalDate date, TrainingModality otra) {
        return covers(date) && appliesTo(otra);
    }
}
