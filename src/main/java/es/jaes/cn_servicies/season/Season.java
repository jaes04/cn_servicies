package es.jaes.cn_servicies.season;

import es.jaes.cn_servicies.club.Club;
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
 * Temporada deportiva de un club. De ella colgaran los grupos, porque
 * entrenador, horario y composicion cambian cada año.
 *
 * <p><b>No lleva borrado, ni logico ni fisico.</b> Conservar las temporadas
 * anteriores es el motivo por el que esta entidad existe: el historico de
 * grupos y asistencia cuelga de aqui, y borrar una temporada se llevaria por
 * delante justo lo que se queria guardar. Una temporada que ya paso se queda,
 * sin mas, con {@code active = false}.
 *
 * <p><b>Solo una activa por club</b>, y eso lo impone la base con un indice
 * unico parcial sobre {@code (club_id) WHERE active}. En el servicio se apaga la
 * anterior antes de encender la nueva; el indice es la red por si alguna vez se
 * llega por otra via.
 */
@Entity
@Table(name = "seasons")
@Data
@NoArgsConstructor
@Filter(name = Club.CLUB_FILTER, condition = Club.CLUB_FILTER_CONDITION)
public class Season {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    /** Como la llama el club: "2026/2027". Unico dentro del club. */
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    /**
     * La temporada en curso. Se enciende con su endpoint, nunca desde el alta:
     * activar es una decision aparte de crear.
     */
    @Column(nullable = false)
    private boolean active = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /** Si la fecha cae dentro de la temporada, extremos incluidos. */
    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
