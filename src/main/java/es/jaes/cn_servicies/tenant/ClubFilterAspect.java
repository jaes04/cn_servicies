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

    @Before("@within(org.springframework.transaction.annotation.Transactional)"
            + " && within(es.jaes.cn_servicies..*)")
    public void activarFiltroDeClub() {
        UUID clubId = TenantContext.get().orElse(null);
        if (clubId == null) {
            return;
        }
        entityManager.unwrap(Session.class)
                .enableFilter(Club.CLUB_FILTER)
                .setParameter(Club.CLUB_FILTER_PARAM, clubId);
    }
}
