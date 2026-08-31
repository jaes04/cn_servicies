package es.jaes.cn_servicies.club;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tenant del sistema. Toda entidad de dominio acabara colgando de un club.
 *
 * No lleva borrado logico a proposito: dar de baja un club es {@code active = false}.
 * Un club con datos asociados no se borra nunca, ni fisica ni logicamente.
 */
@Entity
@Table(name = "clubs")
@Data
@NoArgsConstructor
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** Identificador legible y estable del club. Se usara para resolver dominio o ruta. */
    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private boolean active = true;

    /** Fecha de alta del club. */
    @CreationTimestamp
    private LocalDateTime createdAt;
}
