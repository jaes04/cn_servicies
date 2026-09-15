package es.jaes.cn_servicies.training_group;

import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.season.Season;
import es.jaes.cn_servicies.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Grupo de entrenamiento de una temporada.
 *
 * <p><b>Se llama TrainingGroup y no Group a proposito.</b> {@code GROUP} es
 * palabra reservada de SQL y {@code Group} colisiona con la gramatica de HQL,
 * asi que una entidad con ese nombre obliga a entrecomillar la tabla y a pelear
 * con el parser en cada consulta. El roadmap lo llama "Grupo" y esto es
 * exactamente eso.
 *
 * <p>Cuelga de la temporada porque entrenador, horario y composicion cambian
 * cada año: el grupo "Alevin A" de este curso y el del anterior son dos filas
 * distintas, y eso es lo que permite conservar el historico.
 *
 * <p>Borrado logico: cuando exista {@code AthleteGroup} (1.3) habra
 * pertenencias colgando, y un grupo borrado de verdad se llevaria por delante
 * el historico de quien estuvo en el.
 */
/*
 * El unico (season_id, name) vive en schema.sql como
 * `uk_training_groups_season_name`, no aqui: declararlo en los dos sitios crea
 * dos restricciones equivalentes, y la que genera Hibernate lleva un nombre
 * distinto en cada base.
 */
@Entity
@Table(name = "training_groups")
@Data
@NoArgsConstructor
@SQLRestriction("deleted_at IS NULL")
@Filter(name = Club.CLUB_FILTER, condition = Club.CLUB_FILTER_CONDITION)
public class TrainingGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    /**
     * Entrenador principal. Opcional: en septiembre se montan los grupos antes
     * de cerrar quien lleva cada uno, y al duplicar la temporada anterior el
     * entrenador puede haberse ido del club. Obligarlo forzaria a inventar un
     * titular provisional.
     *
     * <p><b>Estar aqui da acceso al grupo</b> desde la tarea S.3.3.b: a sus
     * sesiones, a su roster y a los atletas que hoy estan en el. Por eso solo
     * puede asignarse a personal tecnico o a un administrador.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coach_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User coach;

    /**
     * Ayudantes. <b>Mismos permisos que el principal</b>: la distincion es de
     * organizacion del club, no de acceso.
     *
     * <p>Las claves foraneas de la tabla intermedia viven en {@code schema.sql}
     * con su {@code ON DELETE}; aqui van sin constraint para que Hibernate no
     * cree las suyas en paralelo, que es el patron de la tarea 2.6.
     *
     * <p>Fuera de {@code toString} y {@code equals}: es una coleccion perezosa, y
     * {@code @Data} la recorreria en cualquier log o mensaje de fallo.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "group_assistant_coaches",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"),
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            inverseForeignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<User> assistantCoaches = new HashSet<>();

    /** Unico dentro de la temporada: dos "Alevin A" el mismo curso son un error. */
    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupLevel level;

    /** Nulo significa sin limite. Obligar a un numero acaba en un 999 que no informa de nada. */
    private Integer maxSlots;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}
