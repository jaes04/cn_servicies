package es.jaes.cn_servicies.user;

import es.jaes.cn_servicies.club.Club;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@SQLRestriction("deleted_at IS NULL")

public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
     private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    /**
     * Unico por club, no global: cada club necesita poder tener su propio
     * 'admin'. La restriccion la impone el indice uk_users_club_username; aqui
     * no se declara unique porque eso seria unicidad global.
     */
    @Column(nullable = false)
    @Getter @Setter private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean blocked = false;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    private String profilePhoto;
}