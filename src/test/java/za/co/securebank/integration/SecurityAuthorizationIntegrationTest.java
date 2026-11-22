package za.co.securebank.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import za.co.securebank.entity.User;
import za.co.securebank.enums.AuditAction;
import za.co.securebank.security.UserPrincipal;

class SecurityAuthorizationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        for (String url : new String[] {"/api/v1/accounts", "/api/v1/users/me", "/api/v1/transactions",
                "/api/v1/admin/users", "/api/v1/admin/transactions"}) {
            JsonNode body = getJson(url, null, 401);
            assertEquals(401, body.get("status").asInt());
        }
        postJson("/api/v1/transactions/deposit", Map.of("accountId", 1, "amount", 10), null, 401);
    }

    @Test
    void malformedTokenIsRejected() throws Exception {
        getJson("/api/v1/accounts", "not.a.jwt", 401);
        getJson("/api/v1/accounts", "garbage", 401);
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        Tokens tokens = registerAndLogin("alice@example.com");
        String[] parts = tokens.accessToken().split("\\.");
        char original = parts[2].charAt(5);
        char replacement = original == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + parts[2].substring(0, 5) + replacement + parts[2].substring(6);

        getJson("/api/v1/accounts", tampered, 401);
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        register("alice@example.com");
        User user = userRepository.findByEmail("alice@example.com").orElseThrow();
        String expired = jwtService.generateAccessToken(UserPrincipal.from(user), Duration.ofSeconds(-60));

        JsonNode body = getJson("/api/v1/accounts", expired, 401);
        assertEquals("The access token is invalid or has expired", body.get("message").asText());
    }

    @Test
    void tokenOfADeletedUserIsRejected() throws Exception {
        Tokens tokens = registerAndLogin("alice@example.com");
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAll();

        getJson("/api/v1/accounts", tokens.accessToken(), 401);
    }

    @Test
    void normalUsersCannotUseAdministrativeEndpoints() throws Exception {
        Tokens user = registerAndLogin("alice@example.com");
        long someId = userId("alice@example.com");

        getJson("/api/v1/admin/users", user.accessToken(), 403);
        getJson("/api/v1/admin/transactions", user.accessToken(), 403);
        getJson("/api/v1/admin/accounts", user.accessToken(), 403);
        getJson("/api/v1/admin/audit-logs", user.accessToken(), 403);
        patchJson("/api/v1/admin/users/" + someId + "/suspend", user.accessToken(), 403);
        patchJson("/api/v1/admin/users/" + someId + "/activate", user.accessToken(), 403);
        assertTrue(auditLogRepository.findAll().stream().anyMatch(l -> l.getAction() == AuditAction.ACCESS_DENIED));
    }

    @Test
    void administratorsCanUseAdministrativeEndpoints() throws Exception {
        registerAndLogin("alice@example.com");
        Tokens admin = createAdminAndLogin("admin@example.com");

        JsonNode users = getJson("/api/v1/admin/users", admin.accessToken(), 200);
        assertEquals(2, users.get("totalElements").asInt());
        getJson("/api/v1/admin/users/" + userId("alice@example.com"), admin.accessToken(), 200);
        getJson("/api/v1/admin/accounts", admin.accessToken(), 200);
        getJson("/api/v1/admin/transactions", admin.accessToken(), 200);
        JsonNode logs = getJson("/api/v1/admin/audit-logs", admin.accessToken(), 200);
        assertTrue(logs.get("totalElements").asInt() > 0);
    }

    @Test
    void suspendingAUserImmediatelyInvalidatesTheirTokens() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        Tokens admin = createAdminAndLogin("admin@example.com");
        getJson("/api/v1/accounts", alice.accessToken(), 200);

        JsonNode suspended = patchJson("/api/v1/admin/users/" + userId("alice@example.com") + "/suspend",
                admin.accessToken(), 200);
        assertEquals("SUSPENDED", suspended.get("status").asText());

        getJson("/api/v1/accounts", alice.accessToken(), 401);
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", alice.refreshToken()), null, 401);
        postJson("/api/v1/auth/login", Map.of("email", "alice@example.com", "password", PASSWORD), null, 403);

        JsonNode active = patchJson("/api/v1/admin/users/" + userId("alice@example.com") + "/activate",
                admin.accessToken(), 200);
        assertEquals("ACTIVE", active.get("status").asText());
        login("alice@example.com", PASSWORD);
    }

    @Test
    void administratorsCannotSuspendThemselves() throws Exception {
        Tokens admin = createAdminAndLogin("admin@example.com");

        patchJson("/api/v1/admin/users/" + userId("admin@example.com") + "/suspend", admin.accessToken(), 400);
    }

    @Test
    void suspendingAnUnknownUserIsNotFound() throws Exception {
        Tokens admin = createAdminAndLogin("admin@example.com");

        patchJson("/api/v1/admin/users/999999/suspend", admin.accessToken(), 404);
    }

    @Test
    void healthEndpointIsPublicButOtherActuatorEndpointsAreNot() throws Exception {
        getJson("/actuator/health", null, 200);
        getJson("/actuator/env", null, 401);
        Tokens user = registerAndLogin("alice@example.com");
        getJson("/actuator/env", user.accessToken(), 403);
    }

    @Test
    void errorResponsesNeverLeakInternalDetails() throws Exception {
        Tokens user = registerAndLogin("alice@example.com");

        JsonNode body = getJson("/api/v1/accounts/999999", user.accessToken(), 404);

        assertEquals("Account not found", body.get("message").asText());
        assertEquals(false, body.has("trace"));
        assertEquals(false, body.has("exception"));
    }
}
