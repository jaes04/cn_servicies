package es.jaes.cn_servicies.guardian;

import es.jaes.cn_servicies.athlete.Athlete;
import es.jaes.cn_servicies.club.Club;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Un consentimiento otorgado —o denegado— por un tutor para una finalidad
 * concreta sobre un atleta.
 *
 * <p><b>Registro append-only.</b> Nada se actualiza ni se borra aqui: revocar
 * es escribir {@link #revokedAt}, y volver a consentir es una fila nueva. La
 * secuencia completa de filas es el historial, y ese historial es la prueba que
 * el club tiene que poder ensenar si se la piden (RGPD art. 7.1). Sobrescribir
 * una fila destruiria justamente lo que hay que demostrar.
 *
 * <p>Por eso tampoco lleva borrado logico ni {@code updatedAt}: no hay
 * operacion que modifique una fila existente salvo la revocacion.
 *
 * <p><b>Lleva {@code club_id} aunque se llegue a el por el atleta.</b> Se
 * aparta del criterio de la 0.2 para tablas hijas, y es deliberado: es la
 * anotacion que sostiene la licitud de todo el tratamiento de un menor, asi que
 * merece que el aislamiento lo imponga Postgres y no la confianza en que toda
 * consulta futura pase por el atleta. Cuesta una columna y una policy.
 */
@Entity
@Table(name = "consents")
@Data
@NoArgsConstructor
@Filter(name = Club.CLUB_FILTER, condition = Club.CLUB_FILTER_CONDITION)
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    /** Quien consiente. Nunca nulo: sin sujeto que otorgue, no hay consentimiento. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guardian_id", nullable = false)
    private Guardian guardian;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConsentType type;

    /**
     * Falso es una negativa registrada, no un hueco. Guardarla importa: sin
     * ella no se distingue "dijo que no a la imagen" de "todavia no se le ha
     * preguntado", y esas dos situaciones no permiten lo mismo.
     */
    @Column(nullable = false)
    private boolean granted;

    /**
     * Fecha en que el tutor decidio, que no tiene por que ser la de registro en
     * el sistema —un formulario en papel se teclea despues—. Es la que manda
     * para saber si el atleta era menor de 14 en ese momento.
     */
    @Column(nullable = false)
    private LocalDate decisionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsentEvidenceType evidenceType;

    /**
     * Referencia opaca al soporte de la evidencia: el numero de archivo del
     * formulario en papel, el identificador del correo. <b>No es un campo de
     * notas</b> y no debe contener datos personales ni descripciones libres.
     */
    @Column(length = 100)
    private String evidenceRef;

    /**
     * Solo para {@link ConsentEvidenceType#ONLINE_FORM}, donde forma parte de la
     * prueba de quien consintio. Nulo en los demas casos.
     *
     * <p>La IP es dato personal (TJUE, <i>Breyer</i>): finalidad acotada a
     * acreditar el consentimiento, nunca analitica, y sujeta al plazo de
     * conservacion que se fije para este registro.
     */
    @Column(length = 45)
    private String sourceIp;

    /** Revocacion. Nulo mientras siga vigente; la fila no se borra jamas. */
    private LocalDateTime revokedAt;

    /** Sello de tiempo del registro en el sistema. */
    @CreationTimestamp
    private LocalDateTime createdAt;

    /** Vigente: se otorgo y no se ha revocado. */
    public boolean isActive() {
        return granted && revokedAt == null;
    }
}
