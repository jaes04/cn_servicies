package es.jaes.cn_servicies.guardian;

import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Quien ejerce la patria potestad o la tutela de un atleta menor.
 *
 * <p><b>Es una persona, no una cuenta.</b> El consentimiento de un menor de 14
 * (LOPDGDD art. 7) hay que poder registrarlo en el momento del alta, y en ese
 * momento el tutor todavia no tiene usuario: el flujo real es al reves, primero
 * se da de alta al atleta y despues el tutor se registra con una
 * {@code AthleteInviteKey}. Por eso {@link #user} es opcional y se rellena
 * cuando esa cuenta existe.
 *
 * <p>Convive con {@code UserAthlete} de tipo {@code TUTOR}, que sigue siendo el
 * vinculo de <b>acceso</b>: quien puede ver los datos del atleta. Esta entidad
 * es el sujeto que <b>otorga el consentimiento</b>. No son lo mismo y no se
 * sustituyen: un tutor sin cuenta consiente igual, y un usuario vinculado sin
 * consentimiento registrado no legitima nada.
 *
 * <p>Borrado logico, nunca fisico: un {@code Consent} apunta aqui y la prueba de
 * quien consintio tiene que sobrevivir a que el tutor deje el club.
 */
@Entity
@Table(name = "guardians")
@Data
@NoArgsConstructor
@SQLRestriction("deleted_at IS NULL")
@Filter(name = Club.CLUB_FILTER, condition = Club.CLUB_FILTER_CONDITION)
public class Guardian {

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

    /**
     * Unico por club, mismo criterio que {@code Athlete.dni}: la misma persona
     * puede ser tutora en dos clubes y cada uno tiene su propia ficha. La
     * restriccion la impone el indice uk_guardians_club_dni.
     *
     * <p>Aqui si es obligatorio, a diferencia del atleta: el tutor es siempre
     * mayor de edad y tiene documento.
     */
    /**
     * DNI, NIE o pasaporte, sin espacios y en mayusculas. Unico por club
     * ({@code uk_guardians_club_dni}). Los 20 caracteres los fija {@code schema.sql}:
     * {@code ddl-auto} no cambia la longitud de una columna que ya existe.
     */
    @Column(nullable = false, length = 20)
    private String dni;

    /**
     * Obligatorio: es la via por la que se avisa de la caducidad del certificado
     * medico y por la que se le hace llegar la informacion del tratamiento.
     */
    @Column(nullable = false)
    private String email;

    /** Opcional: el club lo usa para contacto urgente, no lo exige ningun proceso. */
    private String phone;

    /**
     * La cuenta de este tutor, cuando existe. Nulo mientras no se haya
     * registrado. No se exige para consentir.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User user;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}
