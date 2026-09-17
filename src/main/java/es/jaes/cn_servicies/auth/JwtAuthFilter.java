package es.jaes.cn_servicies.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;

    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider, UserDetailsServiceImpl userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            // Solo el de acceso autentica. El de refresco dura una semana y su
            // unico trabajo es pedir uno nuevo en /api/auth/refresh.
            if (jwtTokenProvider.isValid(token, TokenType.ACCESS)) {
                try {
                    String username = jwtTokenProvider.extractUsername(token);
                    UUID tokenClubId = jwtTokenProvider.extractClubId(token);
                    // Se busca la cuenta dentro del club del token, no por
                    // username a secas: con un 'admin' en cada club la busqueda
                    // suelta era ambigua y no autenticaba a ninguno. Y si el
                    // usuario no es de ese club —un token obsoleto— no aparece,
                    // cae en el catch y la peticion sigue sin autenticar.
                    UserDetails userDetails = userDetailsService.loadUserByClubAndUsername(tokenClubId, username);

                    // Una cuenta bloqueada deja de autenticar en el acto, aunque
                    // su token siga siendo valido. Sin esto, bloquear a alguien
                    // no le echaba: seguia trabajando hasta que su token caducaba,
                    // y es lo unico que hoy sirve para cortar una sesion —no hay
                    // lista de revocacion—.
                    if (!userDetails.isEnabled()) {
                        log.warn("Token de una cuenta bloqueada en {}", request.getRequestURI());
                        filterChain.doFilter(request, response);
                        return;
                    }

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());

                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (Exception e) {
                    log.warn("Error al cargar usuario del token en {}: {}", request.getRequestURI(), e.getMessage());
                }
            } else {
                log.warn("Token inválido o expirado en la petición a {}", request.getRequestURI());
            }
        } else {
            log.debug("Sin token en la petición a {}", request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }
}