package es.jaes.cn_servicies.tenant;

import es.jaes.cn_servicies.club.Club;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Activa el filtro de tenancy de Hibernate al entrar en cada metodo
 * transaccional, con el club del {@link TenantContext}.
 *
 * <p><b>Por que un aspecto y no un filtro de servlet.</b> El filtro vive en la
 * sesion de Hibernate, que nace con la transaccion, no con la peticion. Un
 * filtro de servlet corre antes de que exista ninguna sesion —Spring Boot
 * engancha Open Session In View como interceptor del DispatcherServlet, no como
 * filtro—, asi que ahi no hay nada que activar. Enganchado a la transaccion,
 * esto funciona con OSIV activo o desactivado.
 *
 * <p>El orden importa y esta forzado en {@link TenantTransactionConfig}: sin
 * eso, el consejo transaccional de Spring es el mas interno y este aspecto
 * correria <em>antes</em> de que la transaccion empezara, otra vez sin sesion.
 *
 * <p>Si la peticion no tiene club —endpoints publicos, arranque de la
 * aplicacion— no se activa nada. Una consulta sin filtro es lo que corresponde
 * a quien no pertenece a ningun club, y nunca se recurre a uno por defecto.
 *
 * <p><b>Lo que esto no cubre:</b> los filtros de Hibernate no se aplican a las
 * cargas por clave primaria, asi que {@code findById} devuelve la fila aunque
 * sea de otro club. Para eso estan la Row Level Security de la tarea 0.6 y la
 * comprobacion de propiedad en la capa de servicio.
 */
@Aspect
@Component
public class ClubFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Valor de {@code app.club_id} para las transacciones sin club: peticiones
     * anonimas —login, alta de usuario, blog— y arranque de la aplicacion.
     * Tiene que ponerse explicitamente: si la variable no se fija, las policies
     * no dejan ver ninguna fila.
     */
    private static final String SIN_CLUB = "public";

    @Before("@within(org.springframework.transaction.annotation.Transactional)"
            + " && within(es.jaes.cn_servicies..*)")
    public void activarFiltroDeClub() {
        UUID clubId = TenantContext.get().orElse(null);

        // Capa 1: el filtro de Hibernate. Solo cuando hay club; sin el, no hay
        // nada que filtrar y la consulta va sin condicion.
        if (clubId != null) {
            entityManager.unwrap(Session.class)
                    .enableFilter(Club.CLUB_FILTER)
                    .setParameter(Club.CLUB_FILTER_PARAM, clubId);
        }

        // Capa 2: la variable que leen las policies de Row Level Security.
        // Se fija SIEMPRE, tambien sin club: dejarla sin poner significa "no ver
        // nada", que es el estado que protege de un olvido, no el que quiere una
        // peticion anonima legitima.
        //
        // El tercer parametro de set_config es `is_local`: la variable dura lo
        // que la transaccion, igual que un SET LOCAL. Sin eso se quedaria pegada
        // a la conexion, y como el pool las reutiliza, la siguiente transaccion
        // heredaria el club de la anterior — el mismo fallo que evita el
        // finally del TenantFilter, pero un piso mas abajo.
        entityManager.createNativeQuery("SELECT set_config('app.club_id', :valor, true)")
                .setParameter("valor", clubId != null ? clubId.toString() : SIN_CLUB)
                .getSingleResult();
    }
}
