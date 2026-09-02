package es.jaes.cn_servicies.tenant;

import es.jaes.cn_servicies.auth.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El {@code finally} de {@link TenantFilter} no se puede comprobar desde fuera:
 * ningun endpoint expone el contexto, y el fallo que evita —una peticion que
 * hereda el club de la anterior por reutilizacion de hilos— es intermitente por
 * naturaleza. Por eso se prueba aqui.
 */
class TenantFilterTest {

    private static final UUID CLUB_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    private final TenantFilter filter = new TenantFilter();

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void autenticarEnClub(UUID clubId) {
        AuthenticatedUser user = new AuthenticatedUser(
                "alguien", "hash", true, clubId,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Test
    @DisplayName("pone el club del usuario autenticado durante la peticion")
    void poneElClubDuranteLaPeticion() throws ServletException, IOException {
        autenticarEnClub(CLUB_A);
        UUID[] visto = new UUID[1];

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> visto[0] = TenantContext.require());

        assertThat(visto[0]).isEqualTo(CLUB_A);
    }

    @Test
    @DisplayName("lo limpia al terminar")
    void loLimpiaAlTerminar() throws ServletException, IOException {
        autenticarEnClub(CLUB_A);

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> { });

        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    @DisplayName("lo limpia tambien cuando la peticion revienta")
    void loLimpiaAunqueFalle() {
        autenticarEnClub(CLUB_A);
        FilterChain queRevienta = (req, res) -> {
            throw new ServletException("fallo dentro de la peticion");
        };

        assertThatThrownBy(() ->
                filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), queRevienta))
                .isInstanceOf(ServletException.class);

        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    @DisplayName("una peticion anonima no hereda el club de la anterior en el mismo hilo")
    void noHeredaElClubDeLaPeticionAnterior() throws ServletException, IOException {
        // Primera peticion, autenticada.
        autenticarEnClub(CLUB_A);
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> { });

        // Segunda peticion en el mismo hilo, ya sin autenticacion: es lo que
        // hace Tomcat al reutilizar el hilo de su pool.
        SecurityContextHolder.clearContext();
        boolean[] habiaClub = new boolean[1];

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> habiaClub[0] = TenantContext.isSet());

        assertThat(habiaClub[0]).isFalse();
    }

    @Test
    @DisplayName("una peticion anonima no tiene club, y require() falla en vez de inventarlo")
    void anonimaNoTieneClub() throws ServletException, IOException {
        boolean[] tenia = new boolean[1];

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> tenia[0] = TenantContext.isSet());

        assertThat(tenia[0]).isFalse();
        assertThatThrownBy(TenantContext::require).isInstanceOf(IllegalStateException.class);
    }
}
