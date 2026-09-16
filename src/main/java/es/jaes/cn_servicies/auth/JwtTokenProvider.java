package es.jaes.cn_servicies.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    /** Para que sirve el token. Ver {@link TokenType}. */
    static final String CLAIM_TIPO = "typ";

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public String generateAccessToken(UserDetails user) {
        return buildToken(user, TokenType.ACCESS, expirationMs);
    }

    public String generateRefreshToken(UserDetails user) {
        return buildToken(user, TokenType.REFRESH, refreshExpirationMs);
    }

    private String buildToken(UserDetails user, TokenType type, long expiration) {
        if (!(user instanceof AuthenticatedUser authenticated)) {
            // Preferible reventar aqui que emitir un token sin club: seria
            // invalido en cuanto llegara al filtro, y el fallo apareceria lejos
            // de su causa.
            throw new IllegalStateException(
                    "No se puede emitir un token sin club: se esperaba un AuthenticatedUser");
        }

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("club_id", authenticated.getClubId().toString())
                .claim(CLAIM_TIPO, type.name())
                .claim("roles", user.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key())
                .compact();
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    /**
     * Club al que pertenece el token. Nunca se toma de un parametro, cabecera
     * ni cuerpo de la peticion: solo de aqui, porque el cliente podria enviar
     * otro y la firma es lo unico que hace fiable este valor.
     *
     * @throws JwtException si el token no trae el claim o no es un UUID
     */
    public UUID extractClubId(String token) {
        String clubId = parse(token).get("club_id", String.class);
        if (clubId == null || clubId.isBlank()) {
            throw new MalformedJwtException("El token no incluye el claim club_id");
        }
        try {
            return UUID.fromString(clubId);
        } catch (IllegalArgumentException e) {
            throw new MalformedJwtException("El claim club_id no es un UUID valido");
        }
    }

    /**
     * Para que se emitio el token.
     *
     * @throws JwtException si no trae el claim o trae uno desconocido
     */
    public TokenType extractTokenType(String token) {
        String tipo = parse(token).get(CLAIM_TIPO, String.class);
        if (tipo == null || tipo.isBlank()) {
            throw new MalformedJwtException("El token no dice de que tipo es");
        }
        try {
            return TokenType.valueOf(tipo);
        } catch (IllegalArgumentException e) {
            throw new MalformedJwtException("Tipo de token desconocido: " + tipo);
        }
    }

    /**
     * Si el token vale para lo que se esta pidiendo.
     *
     * <p>No basta con que la firma cuadre. Se exige ademas un {@code club_id}
     * valido —sin el, la peticion no tendria club al que atribuirse— y que el
     * tipo sea el esperado, para que el token de refresco no autentique
     * peticiones y el de acceso no se renueve a si mismo.
     *
     * <p>Un token sin el claim {@code typ} se rechaza siempre: son los emitidos
     * antes de que existiera, y no hay manera de saber para que se hicieron.
     */
    public boolean isValid(String token, TokenType esperado) {
        try {
            extractUsername(token);
            extractClubId(token);
            return extractTokenType(token) == esperado;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}