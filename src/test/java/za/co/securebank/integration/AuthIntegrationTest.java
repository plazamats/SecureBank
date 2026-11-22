package za.co.securebank.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import za.co.securebank.entity.User;
import za.co.securebank.enums.AuditAction;
import za.co.securebank.enums.UserStatus;
import za.co.securebank.util.TokenUtils;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registrationCreatesAUserWithoutExposingPasswordData() throws Exception {
        JsonNode body = postJson("/api/v1/auth/register", Map.of("email", "Alice@Example.com", "password", PASSWORD,
                "firstName", "Alice", "lastName", "Smith"), null, 201);

        assertEquals("alice@example.com", body.get("email").asText());
        assertEquals("ROLE_USER", body.get("roles").get(0).asText());
        assertFalse(body.has("password"));
        assertFalse(body.has("passwordHash"));

        User stored = userRepository.findByEmail("alice@example.com").orElseThrow();
        assertNotEquals(PASSWORD, stored.getPasswordHash());
        assertTrue(stored.getPasswordHash().startsWith("$2"));
    }

    @Test
    void registrationRejectsDuplicateEmails() throws Exception {
        register("alice@example.com");
        postJson("/api/v1/auth/register", Map.of("email", "alice@example.com", "password", PASSWORD,
                "firstName", "A", "lastName", "B"), null, 409);
    }

    @Test
    void registrationRejectsWeakPasswordsWithFieldErrors() throws Exception {
        JsonNode body = postJson("/api/v1/auth/register", Map.of("email", "alice@example.com", "password", "weak",
                "firstName", "A", "lastName", "B"), null, 400);

        assertEquals("Validation failed", body.get("message").asText());
        assertEquals("password", body.get("fieldErrors").get(0).get("field").asText());
    }

    @Test
    void registrationRejectsInvalidEmails() throws Exception {
        postJson("/api/v1/auth/register", Map.of("email", "not-an-email", "password", PASSWORD,
                "firstName", "A", "lastName", "B"), null, 400);
    }

    @Test
    void loginReturnsAccessAndRefreshTokens() throws Exception {
        register("alice@example.com");

        JsonNode body = postJson("/api/v1/auth/login", Map.of("email", "alice@example.com", "password", PASSWORD), null, 200);

        assertEquals("Bearer", body.get("tokenType").asText());
        assertFalse(body.get("accessToken").asText().isBlank());
        assertFalse(body.get("refreshToken").asText().isBlank());
        assertTrue(body.get("expiresInSeconds").asLong() > 0);
        assertTrue(auditLogRepository.findAll().stream().anyMatch(l -> l.getAction() == AuditAction.LOGIN_SUCCESS));
    }

    @Test
    void refreshTokensAreStoredOnlyAsHashes() throws Exception {
        register("alice@example.com");
        Tokens tokens = login("alice@example.com", PASSWORD);

        assertTrue(refreshTokenRepository.findByTokenHash(TokenUtils.sha256Hex(tokens.refreshToken())).isPresent());
        assertTrue(refreshTokenRepository.findByTokenHash(tokens.refreshToken()).isEmpty());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameGenericError() throws Exception {
        register("alice@example.com");

        JsonNode wrongPassword = postJson("/api/v1/auth/login",
                Map.of("email", "alice@example.com", "password", "Wr0ng!Passw0rd"), null, 401);
        JsonNode unknownEmail = postJson("/api/v1/auth/login",
                Map.of("email", "ghost@example.com", "password", "Wr0ng!Passw0rd"), null, 401);

        assertEquals("Invalid email or password", wrongPassword.get("message").asText());
        assertEquals(wrongPassword.get("message").asText(), unknownEmail.get("message").asText());
    }

    @Test
    void accountIsLockedAfterRepeatedFailedLogins() throws Exception {
        register("alice@example.com");
        for (int i = 0; i < 5; i++) {
            postJson("/api/v1/auth/login", Map.of("email", "alice@example.com", "password", "Wr0ng!Passw0rd"), null, 401);
        }

        postJson("/api/v1/auth/login", Map.of("email", "alice@example.com", "password", PASSWORD), null, 423);

        User user = userRepository.findByEmail("alice@example.com").orElseThrow();
        assertNotNull(user.getLockedUntil());
        assertTrue(auditLogRepository.findAll().stream().anyMatch(l -> l.getAction() == AuditAction.ACCOUNT_LOCKED));
    }

    @Test
    void anExpiredLockAllowsLoginAgain() throws Exception {
        register("alice@example.com");
        User user = userRepository.findByEmail("alice@example.com").orElseThrow();
        user.setLockedUntil(Instant.now().minusSeconds(5));
        userRepository.save(user);

        login("alice@example.com", PASSWORD);
    }

    @Test
    void suspendedUsersCannotLogIn() throws Exception {
        register("alice@example.com");
        User user = userRepository.findByEmail("alice@example.com").orElseThrow();
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);

        postJson("/api/v1/auth/login", Map.of("email", "alice@example.com", "password", PASSWORD), null, 403);
    }

    @Test
    void refreshRotatesTokensAndRejectsReuseOfTheOldToken() throws Exception {
        Tokens first = registerAndLogin("alice@example.com");

        JsonNode second = postJson("/api/v1/auth/refresh", Map.of("refreshToken", first.refreshToken()), null, 200);
        String secondRefresh = second.get("refreshToken").asText();
        assertNotEquals(first.refreshToken(), secondRefresh);

        postJson("/api/v1/auth/refresh", Map.of("refreshToken", first.refreshToken()), null, 401);
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", secondRefresh), null, 401);
        assertTrue(auditLogRepository.findAll().stream().anyMatch(l -> l.getAction() == AuditAction.TOKEN_REUSE_DETECTED));
    }

    @Test
    void refreshWithUnknownTokenIsRejected() throws Exception {
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", "does-not-exist"), null, 401);
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        Tokens tokens = registerAndLogin("alice@example.com");

        postJson("/api/v1/auth/logout", Map.of("refreshToken", tokens.refreshToken()), null, 200);

        postJson("/api/v1/auth/refresh", Map.of("refreshToken", tokens.refreshToken()), null, 401);
    }

    @Test
    void profileCanBeReadAndUpdated() throws Exception {
        Tokens tokens = registerAndLogin("alice@example.com");

        JsonNode me = getJson("/api/v1/users/me", tokens.accessToken(), 200);
        assertEquals("alice@example.com", me.get("email").asText());

        putJson("/api/v1/users/me", Map.of("firstName", "Alicia", "lastName", "Jones", "phone", "+27 82 123 4567"),
                tokens.accessToken(), 200);

        JsonNode updated = getJson("/api/v1/users/me", tokens.accessToken(), 200);
        assertEquals("Alicia", updated.get("firstName").asText());
        assertEquals("Jones", updated.get("lastName").asText());
        assertEquals("alice@example.com", updated.get("email").asText());
    }

    @Test
    void emailVerificationFlowMarksTheUserAsVerified() throws Exception {
        register("alice@example.com");
        User user = userRepository.findByEmail("alice@example.com").orElseThrow();
        user.setVerificationTokenHash(TokenUtils.sha256Hex("known-token"));
        user.setVerificationTokenExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        userRepository.save(user);

        postJson("/api/v1/auth/verify-email", Map.of("token", "known-token"), null, 200);

        assertTrue(userRepository.findByEmail("alice@example.com").orElseThrow().isEmailVerified());
        postJson("/api/v1/auth/verify-email", Map.of("token", "known-token"), null, 400);
    }

    @Test
    void passwordResetFlowChangesThePasswordAndRevokesSessions() throws Exception {
        Tokens tokens = registerAndLogin("alice@example.com");

        JsonNode known = postJson("/api/v1/auth/forgot-password", Map.of("email", "alice@example.com"), null, 200);
        JsonNode unknown = postJson("/api/v1/auth/forgot-password", Map.of("email", "ghost@example.com"), null, 200);
        assertEquals(known.get("message").asText(), unknown.get("message").asText());

        User user = userRepository.findByEmail("alice@example.com").orElseThrow();
        assertNotNull(user.getResetTokenHash());
        user.setResetTokenHash(TokenUtils.sha256Hex("reset-me"));
        user.setResetTokenExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
        userRepository.save(user);

        postJson("/api/v1/auth/reset-password", Map.of("token", "reset-me", "newPassword", "N3w!Passw0rdXyz"), null, 200);

        postJson("/api/v1/auth/login", Map.of("email", "alice@example.com", "password", PASSWORD), null, 401);
        login("alice@example.com", "N3w!Passw0rdXyz");
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", tokens.refreshToken()), null, 401);
    }
}
