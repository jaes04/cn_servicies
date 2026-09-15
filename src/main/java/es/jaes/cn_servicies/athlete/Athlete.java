package es.jaes.cn_servicies.athlete;

import es.jaes.cn_servicies.club.Club;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "athletes")
@Data
@NoArgsConstructor
@SQLRestriction("deleted_at IS NULL")
@Filter(name = Club.CLUB_FILTER, condition = Club.CLUB_FILTER_CONDITION)
public class Athlete {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private LocalDate birthDate;

    /**
     * Documento de identidad: DNI, NIE o pasaporte. <b>Opcional</b> desde el
     * bloque 3b: muchos nadadores son menores sin DNI, y los extranjeros tienen
     * NIE o pasaporte. Se guarda sin espacios y en mayusculas, y vacio es nulo.
     *
     * <p><b>El campo sigue llamandose {@code dni}</b> aunque ya no guarde solo
     * DNIs: renombrarlo rompe el contrato de la API y las {@code Specification}
     * que lo referencian por texto sin que falle la compilacion.
     *
     * <p>Unico por club, no global: el mismo nadador puede estar en dos clubes y
     * cada uno tiene su ficha. La restriccion la impone el indice
     * uk_athletes_club_dni, que no se declara aqui porque seria global, y que deja
     * convivir a todas las fichas sin documento: Postgres no considera iguales dos
     * nulos. Sin documento, lo que detecta el duplicado es el nombre y la fecha de
     * nacimiento, en {@code AthleteService}.
     *
     * <p>Que la columna admita nulos y 20 caracteres lo hace {@code schema.sql}:
     * {@code ddl-auto} no cambia una columna que ya existe.
     */
    @Column(length = 20)
    private String dni;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "gender_id", nullable = false)
    private GenderEntity gender;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}