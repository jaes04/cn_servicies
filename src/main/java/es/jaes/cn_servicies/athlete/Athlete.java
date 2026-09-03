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
     * Unico por club, no global: el mismo nadador puede estar en dos clubes y
     * cada uno tiene su ficha. La restriccion la impone el indice
     * uk_athletes_club_dni; aqui no se declara unique porque seria global.
     */
    @Column(nullable = false, length = 9)
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