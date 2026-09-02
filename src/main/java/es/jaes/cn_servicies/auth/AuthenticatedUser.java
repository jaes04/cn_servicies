package es.jaes.cn_servicies.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.UUID;

/**
 * {@link org.springframework.security.core.userdetails.UserDetails} del
 * proyecto. Anade el club al que pertenece la cuenta, que es lo que viaja en el
 * claim {@code club_id} del JWT.
 *
 * <p>Existe para que el club llegue hasta la generacion del token sin tener que
 * arrastrarlo como parametro por cada firma del camino.
 */
public class AuthenticatedUser extends User {

    private final UUID clubId;

    public AuthenticatedUser(String username,
                             String passwordHash,
                             boolean enabled,
                             UUID clubId,
                             Collection<? extends GrantedAuthority> authorities) {
        super(username, passwordHash, enabled, true, true, true, authorities);
        this.clubId = clubId;
    }

    public UUID getClubId() {
        return clubId;
    }
}
