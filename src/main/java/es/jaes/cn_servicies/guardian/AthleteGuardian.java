package es.jaes.cn_servicies.guardian;

import es.jaes.cn_servicies.athlete.Athlete;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Vinculo entre un atleta y quien ejerce su tutela. Varios por atleta —lo
 * habitual son dos progenitores— y varios por tutor, que es el caso de los
 * hermanos.
 *
 * <p>No lleva {@code club_id}: es tabla hija y llega a su club por cualquiera
 * de sus dos padres, ambos filtrados. Sigue la regla de la 0.2.
 *
 * <p>Borrado fisico, como {@code UserAthlete}: esto es quien ejerce la tutela
 * hoy, no un registro historico. Lo que si tiene que sobrevivir es el
 * consentimiento, y ese apunta al {@code Guardian}, no a este vinculo.
 */
@Entity
/*
 * El unico va en schema.sql como `uk_athlete_guardians`, no aqui: declararlo en los dos
 * sitios crea dos restricciones equivalentes, y la que genera Hibernate lleva
 * un nombre distinto en cada base.
 */
@Table(name = "athlete_guardians")
@Data
@NoArgsConstructor
public class AthleteGuardian {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Athlete athlete;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guardian_id", nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Guardian guardian;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GuardianRelationship relationship;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
