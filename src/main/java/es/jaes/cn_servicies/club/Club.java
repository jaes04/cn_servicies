package es.jaes.cn_servicies.club;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tenant del sistema. Toda entidad de dominio acabara colgando de un club.
 *
 * No lleva borrado logico a proposito: dar de baja un club es {@code active = false}.
 * Un club con datos asociados no se borra nunca, ni fisica ni logicamente.
 *
 * <p>Aqui se declara el filtro de tenancy, una sola vez para todo el modelo. Se
 * define en esta entidad por ser la raiz del concepto; las entidades que lo
 * aplican lo hacen con {@code @Filter(name = CLUB_FILTER)}.
 *
 * <p><b>El filtro esta declarado pero no se activa todavia.</b> Mientras nadie
 * llame a {@code enableFilter}, estas anotaciones no cambian ninguna consulta.
 */
@Entity
@Table(name = "clubs")
@FilterDef(
        name = Club.CLUB_FILTER,
        parameters = @ParamDef(name = Club.CLUB_FILTER_PARAM, type = UUID.class))
@Data
@NoArgsConstructor
public class Club {

    /** Nombre del filtro de tenancy, para no repetir la cadena por el modelo. */
    public static final String CLUB_FILTER = "clubFilter";

    /** Parametro del filtro: el club de la peticion en curso. */
    public static final String CLUB_FILTER_PARAM = "clubId";

    /** Condicion que se anade a las consultas de las entidades filtradas. */
    public static final String CLUB_FILTER_CONDITION = "club_id = :clubId";

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
