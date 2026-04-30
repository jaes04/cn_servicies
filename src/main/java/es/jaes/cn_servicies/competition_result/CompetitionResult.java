package es.jaes.cn_servicies.competition_result;

import es.jaes.cn_servicies.athlete.Athlete;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "competition_results")
@Data
@NoArgsConstructor
@SQLRestriction("deleted_at IS NULL")
public class CompetitionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Column(nullable = false)
    private LocalDate competitionDate;

    @Column(nullable = false)
    private Integer distanceMeters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Stroke stroke;

    @Column(nullable = false)
    private Integer poolLength;

    /** Tiempo en milisegundos */
    @Column(nullable = false)
    private Long resultTimeMillis;

    @Column(nullable = false)
    private boolean partial = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "final_result_id")
    private CompetitionResult finalResult;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}