package es.jaes.cn_servicies.tenant;

import es.jaes.cn_servicies.auth.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rellena el {@link TenantContext} con el club del usuario autenticado y lo
 * limpia al terminar.
 *
 * <p>Va <b>despues</b> de {@code JwtAuthFilter}: necesita el
 * {@code SecurityContext} ya poblado. Toma el club de {@link AuthenticatedUser},
 * que a su vez lo trae del claim del token ya verificado.
 *
 * <p>Si la peticion es anonima no se pone nada, y eso es lo correcto: un
 * endpoint publico no tiene club. Ver {@link TenantContext}.
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null
                    && auth.isAuthenticated()
                    && auth.getPrincipal() instanceof AuthenticatedUser user) {
                TenantContext.set(user.getClubId());
            }
            filterChain.doFilter(request, response);
        } finally {
            // Lo mas importante del archivo. Tomcat reutiliza los hilos de su
            // pool: sin esta limpieza, la siguiente peticion que caiga en este
            // hilo hereda el club de la anterior. El fallo es intermitente,
            // depende del reparto de hilos y no se reproduce a voluntad.
            TenantContext.clear();
        }
    }
}
