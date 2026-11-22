package za.co.securebank.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import za.co.securebank.config.JwtProperties;

class JwtServiceTest {

    private static final String SECRET = encode("unit-test-secret-key-that-is-at-least-32-bytes-long");
    private static final String OTHER_SECRET = encode("a-completely-different-secret-key-with-32-plus-bytes");

    private JwtService jwtService;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(SECRET, 15, 7, "securebank-api"));
        principal = new UserPrincipal(42L, "alice@example.com", "hash",
                List.of(new SimpleGrantedAuthority("ROLE_USER")), true, null, true);
    }

    @Test
    void generatedTokenIsValidAndCarriesIdentityAndRoles() {
        String token = jwtService.generateAccessToken(principal);

        assertTrue(jwtService.isTokenValid(token));
        assertEquals("alice@example.com", jwtService.extractUsername(token));
        Claims claims = jwtService.parseClaims(token);
        assertEquals(42L, ((Number) claims.get("uid")).longValue());
        assertEquals(List.of("ROLE_USER"), claims.get("roles", List.class));
        assertNotNull(claims.getId());
        assertEquals("securebank-api", claims.getIssuer());
    }

    @Test
    void tokenNeverContainsThePasswordHash() {
        String token = jwtService.generateAccessToken(principal);
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertFalse(payload.contains("hash"));
        assertFalse(payload.contains("password"));
    }

    @Test
    void expiredTokenIsRejected() {
        String expired = jwtService.generateAccessToken(principal, Duration.ofSeconds(-30));

        assertFalse(jwtService.isTokenValid(expired));
        assertThrows(ExpiredJwtException.class, () -> jwtService.parseClaims(expired));
    }

    @Test
    void tamperedSignatureIsRejected() {
        String token = jwtService.generateAccessToken(principal);
        String[] parts = token.split("\\.");
        char original = parts[2].charAt(5);
        char replacement = original == 'A' ? 'B' : 'A';
        String tamperedSignature = parts[2].substring(0, 5) + replacement + parts[2].substring(6);
        String tampered = parts[0] + "." + parts[1] + "." + tamperedSignature;

        assertFalse(jwtService.isTokenValid(tampered));
        assertThrows(JwtException.class, () -> jwtService.parseClaims(tampered));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService attacker = new JwtService(new JwtProperties(OTHER_SECRET, 15, 7, "securebank-api"));
        String forged = attacker.generateAccessToken(principal);

        assertFalse(jwtService.isTokenValid(forged));
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        JwtService otherIssuer = new JwtService(new JwtProperties(SECRET, 15, 7, "someone-else"));
        String token = otherIssuer.generateAccessToken(principal);

        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void garbageAndBlankTokensAreRejected() {
        assertFalse(jwtService.isTokenValid("not-a-jwt"));
        assertFalse(jwtService.isTokenValid(""));
        assertFalse(jwtService.isTokenValid("a.b.c"));
    }

    @Test
    void secretShorterThan256BitsIsRejectedAtStartup() {
        String shortSecret = encode("too-short");
        assertThrows(IllegalStateException.class,
                () -> new JwtService(new JwtProperties(shortSecret, 15, 7, "securebank-api")));
    }

    @Test
    void secretThatIsNotBase64IsRejectedAtStartup() {
        assertThrows(IllegalStateException.class,
                () -> new JwtService(new JwtProperties("this is *not* base64 !!", 15, 7, "securebank-api")));
    }

    @Test
    void accessTokenTtlComesFromConfiguration() {
        assertEquals(900L, jwtService.getAccessTokenTtlSeconds());
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
