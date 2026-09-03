package es.jaes.cn_servicies.tenant;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Pone el consejo transaccional de Spring por fuera de todo lo demas.
 *
 * <p>Por defecto va con {@code LOWEST_PRECEDENCE}, es decir, es el mas interno:
 * la transaccion empieza justo antes del metodo y despues de cualquier aspecto.
 * Con ese orden, {@link ClubFilterAspect} correria <em>antes</em> de que
 * existiera la sesion de Hibernate y no tendria sobre que activar el filtro.
 *
 * <p>Invirtiendolo, la transaccion se abre primero y el aspecto entra con la
 * sesion ya viva.
 *
 * <p>Existe solo por la tenancy, de ahi que viva en este paquete y no en
 * {@code config/}. Afecta al orden de todos los consejos de la aplicacion, asi
 * que conviene recordarlo si algun dia se anaden {@code @Async},
 * {@code @Cacheable} o validacion por aspectos.
 */
@Configuration
@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE)
public class TenantTransactionConfig {
}
