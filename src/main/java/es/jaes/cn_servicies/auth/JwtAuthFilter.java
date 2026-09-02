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
            if (jwtTokenProvider.isValid(token)) {
                try {
                    String username = jwtTokenProvider.extractUsername(token);
                    UUID tokenClubId = jwtTokenProvider.extractClubId(token);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    // El club del token tiene que ser el del usuario. La firma
                    // ya impide falsificarlo, pero un token puede quedar obsoleto
                    // y no se autentica a nadie en un club que no es el suyo.
                    if (!(userDetails instanceof AuthenticatedUser authenticated)
                            || !authenticated.getClubId().equals(tokenClubId)) {
                        log.warn("El club del token no coincide con el del usuario en {}",
                                request.getRequestURI());
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