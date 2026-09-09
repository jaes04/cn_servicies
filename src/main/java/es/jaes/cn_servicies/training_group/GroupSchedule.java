package es.jaes.cn_servicies.training_group;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Horario recurrente de un grupo: "los martes de 18:00 a 19:00, en el agua".
 *
 * <p>Es la <b>plantilla</b> de la que la 2.2 materializa las sesiones. No es
 * un entrenamiento: no tiene fecha, tiene dia de la semana. La diferencia
 * importa porque cambiar un horario no puede reescribir el pasado —lo que ya
 * ocurrio ocurrio— sino solo lo que aun no se ha entrenado.
 *
 * <p>Un grupo tiene <b>varios</b>: lunes, miercoles y viernes son tres filas, y
 * el martes de seco es una cuarta. Que la modalidad viva aqui y no en el grupo
 * es lo que evita partir "Alevin A" en dos grupos con los mismos nadadores.
 *
 * <p><b>{@code validUntil} es el ULTIMO DIA de vigencia, incluido.</b> Es el
 * mismo criterio que {@code AthleteGroup.leftOn} y se hereda a proposito: en la
 * 2.2 un error de un dia aqui no es un dia, es una sesion fantasma o una sesion
 * que falta, multiplicada por cada semana del rango generado.
 *
 * <p>No lleva {@code club_id}: es tabla hija y llega a su club por el grupo,
 * que si esta bajo policy. La contrapartida es que un {@code findById} suyo por
 * clave primaria no esta tapado por RLS, y por eso <b>todas las rutas van
 * anidadas bajo el grupo</b> y el servicio comprueba la pertenencia antes de
 * devolver nada. Ver {@link GroupScheduleService#delGrupoOException}.
 *
 * <p>Borrado logico, como {@link TrainingGroup}: en la 2.2 las sesiones
 * colgaran del horario que las genero, y borrar la fila las dejaria sin explicar
 * de donde salieron.
 */
@Entity
@Table(name = "group_schedules")
@Data
@NoArgsConstructor
@SQLRestriction("deleted_at IS NULL")
public class GroupSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * El campo se llama {@code trainingGroup} y no {@code group} porque
     * {@code group} es palabra reservada tambien en HQL: {@code h.group} rompe
     * cualquier consulta que lo use.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private TrainingGroup trainingGroup;

    /**
     * {@link DayOfWeek} de {@code java.time}, no un entero. Guardado como texto
     * para no depender de si la semana empieza en lunes o en domingo, que es
     * justo el tipo de suposicion que rompe las sesiones de los fines de semana.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 20)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrainingModality modality;

    /** Primer dia en que este horario esta en vigor. Cae dentro de la temporada del grupo. */
    @Column(nullable = false)
    private LocalDate validFrom;

    /** Nulo mientras siga vigente. Si tiene valor, es el ultimo dia de vigencia, incluido. */
    private LocalDate validUntil;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    /** Si el horario esta en vigor esa fecha, con los dos extremos incluidos. */
    public boolean isInForceOn(LocalDate date) {
        return !validFrom.isAfter(date) && (validUntil == null || !validUntil.isBefore(date));
    }

    /**
     * Si dos horarios del mismo grupo se pisan: mismo dia de la semana, franjas
     * horarias que se solapan y periodos de vigencia que coinciden. Los tres a
     * la vez, porque fallando uno solo no hay conflicto: el mismo martes a la
     * misma hora en trimestres distintos son dos sesiones distintas.
     */
    public boolean conflictsWith(GroupSchedule other) {
        return dayOfWeek == other.dayOfWeek
                && solapaEnHora(other)
                && solapaEnVigencia(other);
    }

    /**
     * Estricto en los extremos: 17:00–18:00 y 18:00–19:00 se tocan pero no se
     * pisan, y encadenar el grupo de agua con el de seco es un caso real.
     */
    private boolean solapaEnHora(GroupSchedule other) {
        return startTime.isBefore(other.endTime) && other.startTime.isBefore(endTime);
    }

    /** Con {@code validUntil} nulo entendido como "sin fin". */
    private boolean solapaEnVigencia(GroupSchedule other) {
        boolean empiezoAntesDeQueAcabe =
                other.validUntil == null || !validFrom.isAfter(other.validUntil);
        boolean empiezaAntesDeQueAcabe =
                validUntil == null || !other.validFrom.isAfter(validUntil);
        return empiezoAntesDeQueAcabe && empiezaAntesDeQueAcabe;
    }
}
