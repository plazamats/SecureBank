package za.co.securebank.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;
import za.co.securebank.config.JwtProperties;

/**
 * Issues and validates short lived HS256 access tokens. The signing key comes from configuration (environment).
 */
@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(properties.secret());
        } catch (RuntimeException ex) {
            throw new IllegalStateException("JWT_SECRET must be a valid Base64 string", ex);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes (256 bits)");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UserPrincipal principal) {
        return generateAccessToken(principal, Duration.ofMinutes(properties.accessTokenMinutes()));
    }

    /** Visible so that tests can mint tokens with a custom (for example already expired) lifetime. */
    public String generateAccessToken(UserPrincipal principal, Duration timeToLive) {
        Instant now = Instant.now();
        List<String> roles = principal.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .subject(principal.getUsername())
                .claim("uid", principal.getId())
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(timeToLive)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifies signature, issuer and expiry and returns the claims.
     *
     * @throws JwtException             when the token is malformed, tampered with, expired or has the wrong issuer
     * @throws IllegalArgumentException when the token is blank
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public long getAccessTokenTtlSeconds() {
        return Duration.ofMinutes(properties.accessTokenMinutes()).toSeconds();
    }
}
