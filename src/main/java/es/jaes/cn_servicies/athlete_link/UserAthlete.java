package es.jaes.cn_servicies.athlete_link;

import es.jaes.cn_servicies.athlete.Athlete;
import es.jaes.cn_servicies.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "user_athletes",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "athlete_id"})
)
@Data
@NoArgsConstructor
public class UserAthlete {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserAthleteType type;

    @CreationTimestamp
    private LocalDateTime createdAt;
}