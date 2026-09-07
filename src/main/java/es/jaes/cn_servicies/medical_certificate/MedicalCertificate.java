package es.jaes.cn_servicies.medical_certificate;

import es.jaes.cn_servicies.athlete.Athlete;
import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Certificado medico federativo de un atleta. <b>Solo metadatos.</b>
 *
 * <p><b>No se almacena el veredicto, y no es un olvido.</b> Nadie presenta un
 * certificado de "no apto": si existe uno en plazo, esa es la aptitud. Un campo
 * {@code apto} seria un juicio clinico y convertiria esta tabla en datos de
 * salud del art. 9 RGPD; una fecha de caducidad no lo es. Lo que la federacion
 * necesita saber es que hay certificado y hasta cuando.
 *
 * <p><b>Nunca anadas aqui</b> diagnosticos, patologias, antecedentes, alergias,
 * medicacion, tratamientos ni ningun campo de texto libre. Un campo de notas en
 * esta entidad acaba recogiendo exactamente lo que no debe. Si una tarea futura
 * pide guardar el PDF, eso es la Opcion B de la S.1.b y son otras siete
 * restricciones: para, y preguntalo.
 *
 * <p>No lleva {@code season_id}: la vigencia la definen sus propias fechas, y
 * {@code Season} no existe hasta la Fase 1. Tampoco lleva estado almacenado —se
 * calcula desde {@link #expiresOn}— porque un estado a mano se queda obsoleto
 * solo con que pase un dia.
 *
 * <p>Lleva {@code club_id} con policy de RLS propia, misma decision explicita
 * que en {@code consents}: es lo mas sensible que toca esta fase y el
 * aislamiento merece imponerlo Postgres.
 */
@Entity
@Table(name = "medical_certificates")
@Data
@NoArgsConstructor
@Filter(name = Club.CLUB_FILTER, condition = Club.CLUB_FILTER_CONDITION)
public class MedicalCertificate {

    /** Un certificado se avisa cuando le quedan menos de estos dias. */
    public static final int EXPIRY_WARNING_DAYS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Column(nullable = false)
    private LocalDate issuedOn;

    @Column(nullable = false)
    private LocalDate expiresOn;

    /** Quien del club comprobo el papel. Rinde cuentas de que el registro es real. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "validated_by_id", nullable = false)
    private User validatedBy;

    @Column(nullable = false)
    private LocalDateTime validatedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    /** Derivado de la fecha de caducidad, nunca almacenado. */
    public MedicalCertificateStatus statusOn(LocalDate date) {
        if (expiresOn.isBefore(date)) {
            return MedicalCertificateStatus.EXPIRED;
        }
        if (expiresOn.isBefore(date.plusDays(EXPIRY_WARNING_DAYS))) {
            return MedicalCertificateStatus.EXPIRING_SOON;
        }
        return MedicalCertificateStatus.VALID;
    }
}
