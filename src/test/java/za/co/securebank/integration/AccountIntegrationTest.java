package za.co.securebank.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import za.co.securebank.entity.BankAccount;

class AccountIntegrationTest extends AbstractIntegrationTest {

    @Test
    void newAccountStartsWithZeroBalanceAndATenDigitNumber() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");

        JsonNode account = createAccount(alice.accessToken(), "SAVINGS");

        assertTrue(account.get("accountNumber").asText().matches("\\d{10}"));
        assertEquals(0, BigDecimal.ZERO.compareTo(new BigDecimal(account.get("balance").asText())));
        assertEquals("ZAR", account.get("currency").asText());
        assertEquals("ACTIVE", account.get("status").asText());
    }

    @Test
    void ownerIsAlwaysTakenFromTheSecurityContextNeverFromTheRequest() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        registerAndLogin("bob@example.com");
        long bobId = userId("bob@example.com");

        JsonNode account = postJson("/api/v1/accounts",
                Map.of("type", "CHECKING", "name", "Sneaky", "ownerId", bobId, "userId", bobId), alice.accessToken(), 201);

        BankAccount stored = accountRepository.findById(account.get("id").asLong()).orElseThrow();
        assertEquals(userId("alice@example.com"), stored.getOwner().getId());
    }

    @Test
    void listReturnsOnlyTheCallersAccounts() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        Tokens bob = registerAndLogin("bob@example.com");
        createAccount(alice.accessToken(), "SAVINGS");
        createAccount(alice.accessToken(), "CHECKING");
        createAccount(bob.accessToken(), "CHECKING");

        assertEquals(2, getJson("/api/v1/accounts", alice.accessToken(), 200).size());
        assertEquals(1, getJson("/api/v1/accounts", bob.accessToken(), 200).size());
    }

    @Test
    void usersCannotReadAnotherUsersAccountOrBalance() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        Tokens bob = registerAndLogin("bob@example.com");
        long aliceAccount = createAccount(alice.accessToken(), "CHECKING").get("id").asLong();

        getJson("/api/v1/accounts/" + aliceAccount, alice.accessToken(), 200);
        getJson("/api/v1/accounts/" + aliceAccount, bob.accessToken(), 404);
        getJson("/api/v1/accounts/" + aliceAccount + "/balance", bob.accessToken(), 404);
    }

    @Test
    void invalidAccountTypeIsRejected() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");

        postJson("/api/v1/accounts", Map.of("type", "OFFSHORE"), alice.accessToken(), 400);
        postJson("/api/v1/accounts", Map.of("name", "no type"), alice.accessToken(), 400);
    }

    @Test
    void accountCreationIsLimitedPerUser() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        for (int i = 0; i < 5; i++) {
            createAccount(alice.accessToken(), "SAVINGS");
        }

        postJson("/api/v1/accounts", Map.of("type", "SAVINGS"), alice.accessToken(), 422);
    }
}
