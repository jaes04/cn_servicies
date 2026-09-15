package es.jaes.cn_servicies.document_delivery;

import es.jaes.cn_servicies.athlete.Athlete;
import es.jaes.cn_servicies.club.Club;
import es.jaes.cn_servicies.season.Season;
import es.jaes.cn_servicies.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Constancia de que la familia entrego un papel al club.
 *
 * <p><b>El papel no esta aqui.</b> Se queda en el club, que es el responsable del
 * tratamiento; el sistema solo apunta que se entrego, cuando y hasta cuando vale.
 * Fue la propuesta del primer club y es lo que permite no almacenar archivos en
 * el despliegue inicial: sin archivo no hay titulo libre, ni nombre original, ni
 * contenido que pueda llevar un dato de salud.
 *
 * <p><b>Sin numero de documento, sin destino del viaje y sin notas.</b> El numero
 * del DNI ya esta en la ficha del atleta; el destino no hace falta para saber si
 * un permiso cubre unas fechas; y un campo de notas en un registro de menores es
 * la via mas corta a que alguien escriba lo que no debe.
 *
 * <p>Que campos lleva cada tipo lo valida el servicio: una licencia sin temporada
 * o un permiso sin fechas se rechazan. Ver {@link DocumentDeliveryType}.
 *
 * <p>Lleva {@code club_id} y policy de RLS propia
 * ({@code migrations/S.1-document-deliveries-rls.sql}), como el certificado
 * medico: es entidad raiz y su estado se consulta por atleta suelto.
 */
@Entity
@Table(name = "document_deliveries")
@Data
@NoArgsConstructor
@Filter(name = Club.CLUB_FILTER, condition = Club.CLUB_FILTER_CONDITION)
public class DocumentDelivery {

    /** Un papel se avisa cuando le quedan menos de estos dias. El mismo margen que el certificado. */
    public static final int EXPIRY_WARNING_DAYS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    @ToString.Exclude
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @ToString.Exclude
    private Athlete athlete;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentDeliveryType type;

    /** Solo en la licencia, que vale para una temporada concreta. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id")
    @ToString.Exclude
    private Season season;

    /** Solo en el permiso de viaje: el dia de salida. */
    private LocalDate validFrom;

    /**
     * Ultimo dia en que vale, incluido. En el documento de identidad es su
     * caducidad; en el permiso de viaje, el dia de vuelta. La licencia no lo
     * lleva: su ultimo dia es el de la temporada.
     */
    private LocalDate validUntil;

    /** El dia que la familia trajo el papel. */
    @Column(nullable = false)
    private LocalDate deliveredOn;

    /** Quien del club lo anoto. Rinde cuentas de que el registro es real. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registered_by_id", nullable = false)
    @ToString.Exclude
    private User registeredBy;

    @Column(nullable = false)
    private LocalDateTime registeredAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    /** Ultimo dia en que el papel vale, sea cual sea el tipo. */
    public LocalDate lastValidDay() {
        return type == DocumentDeliveryType.LICENSE_APPLICATION ? season.getEndDate() : validUntil;
    }
}
