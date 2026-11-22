package za.co.securebank.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import za.co.securebank.enums.AuditAction;

class TransactionIntegrationTest extends AbstractIntegrationTest {

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private JsonNode withdraw(String token, long accountId, String amount, int expectedStatus) throws Exception {
        return postJson("/api/v1/transactions/withdraw",
                Map.of("accountId", accountId, "amount", new BigDecimal(amount)), token, expectedStatus);
    }

    private JsonNode transfer(String token, long sourceId, String destinationNumber, String amount, int expectedStatus)
            throws Exception {
        return postJson("/api/v1/transactions/transfer", Map.of("sourceAccountId", sourceId,
                "destinationAccountNumber", destinationNumber, "amount", new BigDecimal(amount)), token, expectedStatus);
    }

    @Test
    void depositIncreasesTheBalanceAndReturnsAReference() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        long account = createAccount(alice.accessToken(), "CHECKING").get("id").asLong();

        JsonNode tx = deposit(alice.accessToken(), account, "100.50");

        assertEquals("DEPOSIT", tx.get("type").asText());
        assertTrue(tx.get("reference").asText().startsWith("TXN-"));
        assertMoney("100.50", new BigDecimal(tx.get("balanceAfter").asText()));
        assertMoney("100.50", balanceOf(alice.accessToken(), account));
    }

    @Test
    void withdrawalReducesTheBalance() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        long account = createAccount(alice.accessToken(), "CHECKING").get("id").asLong();
        deposit(alice.accessToken(), account, "100.00");

        JsonNode tx = withdraw(alice.accessToken(), account, "40.25", 201);

        assertEquals("WITHDRAWAL", tx.get("type").asText());
        assertMoney("59.75", balanceOf(alice.accessToken(), account));
    }

    @Test
    void overdraftIsRejectedAndTheBalanceIsUnchanged() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        long account = createAccount(alice.accessToken(), "CHECKING").get("id").asLong();
        deposit(alice.accessToken(), account, "50.00");

        JsonNode error = withdraw(alice.accessToken(), account, "50.01", 422);

        assertEquals("Insufficient funds", error.get("message").asText());
        assertMoney("50.00", balanceOf(alice.accessToken(), account));
        assertTrue(auditLogRepository.findAll().stream()
                .anyMatch(l -> l.getAction() == AuditAction.TRANSACTION_REJECTED));
    }

    @Test
    void invalidAmountsAreRejected() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        long account = createAccount(alice.accessToken(), "CHECKING").get("id").asLong();

        for (String amount : new String[] {"0", "0.00", "-5.00", "10.001"}) {
            postJson("/api/v1/transactions/deposit",
                    Map.of("accountId", account, "amount", new BigDecimal(amount)), alice.accessToken(), 400);
        }
        postJson("/api/v1/transactions/deposit", Map.of("accountId", account), alice.accessToken(), 400);
        assertMoney("0.00", balanceOf(alice.accessToken(), account));
    }

    @Test
    void usersCannotMoveMoneyInAnotherUsersAccount() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        Tokens bob = registerAndLogin("bob@example.com");
        long aliceAccount = createAccount(alice.accessToken(), "CHECKING").get("id").asLong();
        deposit(alice.accessToken(), aliceAccount, "100.00");

        deposit(bob.accessToken(), aliceAccount, "5.00", 404);
        withdraw(bob.accessToken(), aliceAccount, "5.00", 404);
        assertMoney("100.00", balanceOf(alice.accessToken(), aliceAccount));
    }

    private void deposit(String token, long accountId, String amount, int expectedStatus) throws Exception {
        postJson("/api/v1/transactions/deposit",
                Map.of("accountId", accountId, "amount", new BigDecimal(amount)), token, expectedStatus);
    }

    @Test
    void transferMovesMoneyBetweenUsersAndRespectsVisibility() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        Tokens bob = registerAndLogin("bob@example.com");
        Tokens charlie = registerAndLogin("charlie@example.com");
        JsonNode aliceAccount = createAccount(alice.accessToken(), "CHECKING");
        JsonNode bobAccount = createAccount(bob.accessToken(), "SAVINGS");
        deposit(alice.accessToken(), aliceAccount.get("id").asLong(), "200.00");

        JsonNode tx = transfer(alice.accessToken(), aliceAccount.get("id").asLong(),
                bobAccount.get("accountNumber").asText(), "75.25", 201);

        assertEquals("TRANSFER", tx.get("type").asText());
        assertMoney("124.75", balanceOf(alice.accessToken(), aliceAccount.get("id").asLong()));
        assertMoney("75.25", balanceOf(bob.accessToken(), bobAccount.get("id").asLong()));
        assertMoney("124.75", new BigDecimal(tx.get("balanceAfter").asText()));

        long txId = tx.get("id").asLong();
        getJson("/api/v1/transactions/" + txId, alice.accessToken(), 200);
        JsonNode bobView = getJson("/api/v1/transactions/" + txId, bob.accessToken(), 200);
        assertMoney("75.25", new BigDecimal(bobView.get("balanceAfter").asText()));
        getJson("/api/v1/transactions/" + txId, charlie.accessToken(), 404);
    }

    @Test
    void transferRulesAreEnforced() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        Tokens bob = registerAndLogin("bob@example.com");
        JsonNode aliceAccount = createAccount(alice.accessToken(), "CHECKING");
        JsonNode bobAccount = createAccount(bob.accessToken(), "CHECKING");
        long aliceId = aliceAccount.get("id").asLong();
        deposit(alice.accessToken(), aliceId, "50.00");

        transfer(alice.accessToken(), aliceId, aliceAccount.get("accountNumber").asText(), "10.00", 400);
        transfer(alice.accessToken(), aliceId, bobAccount.get("accountNumber").asText(), "50.01", 422);
        transfer(alice.accessToken(), aliceId, "9999999999", "10.00", 404);
        transfer(bob.accessToken(), aliceId, bobAccount.get("accountNumber").asText(), "10.00", 404);
        postJson("/api/v1/transactions/transfer", Map.of("sourceAccountId", aliceId,
                "destinationAccountNumber", "abc", "amount", new BigDecimal("1.00")), alice.accessToken(), 400);

        assertMoney("50.00", balanceOf(alice.accessToken(), aliceId));
        assertMoney("0.00", balanceOf(bob.accessToken(), bobAccount.get("id").asLong()));
    }

    @Test
    void historyCanBeFilteredByTypeAndDateAndIsPaged() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        long account = createAccount(alice.accessToken(), "CHECKING").get("id").asLong();
        deposit(alice.accessToken(), account, "100.00");
        deposit(alice.accessToken(), account, "50.00");
        withdraw(alice.accessToken(), account, "30.00", 201);
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));

        assertEquals(3, getJson("/api/v1/transactions", alice.accessToken(), 200).get("totalElements").asInt());
        assertEquals(2, getJson("/api/v1/transactions?type=DEPOSIT", alice.accessToken(), 200).get("totalElements").asInt());
        assertEquals(1, getJson("/api/v1/transactions?type=WITHDRAWAL", alice.accessToken(), 200).get("totalElements").asInt());
        assertEquals(3, getJson("/api/v1/transactions?from=" + today + "&to=" + today, alice.accessToken(), 200)
                .get("totalElements").asInt());
        assertEquals(0, getJson("/api/v1/transactions?from=" + today.plusDays(1), alice.accessToken(), 200)
                .get("totalElements").asInt());
        assertEquals(0, getJson("/api/v1/transactions?to=" + today.minusDays(1), alice.accessToken(), 200)
                .get("totalElements").asInt());
        assertEquals(3, getJson("/api/v1/transactions?accountId=" + account, alice.accessToken(), 200)
                .get("totalElements").asInt());

        JsonNode page = getJson("/api/v1/transactions?size=2&page=0", alice.accessToken(), 200);
        assertEquals(2, page.get("content").size());
        assertEquals(2, page.get("totalPages").asInt());
        assertEquals("WITHDRAWAL", page.get("content").get(0).get("type").asText());
    }

    @Test
    void historyRejectsBadFiltersAndForeignAccounts() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        Tokens bob = registerAndLogin("bob@example.com");
        long bobAccount = createAccount(bob.accessToken(), "CHECKING").get("id").asLong();
        createAccount(alice.accessToken(), "CHECKING");

        getJson("/api/v1/transactions?type=BOGUS", alice.accessToken(), 400);
        getJson("/api/v1/transactions?from=2026-12-31&to=2026-01-01", alice.accessToken(), 400);
        getJson("/api/v1/transactions?from=not-a-date", alice.accessToken(), 400);
        getJson("/api/v1/transactions?accountId=" + bobAccount, alice.accessToken(), 404);
    }

    @Test
    void historyOfAUserWithoutAccountsIsEmpty() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");

        JsonNode page = getJson("/api/v1/transactions", alice.accessToken(), 200);

        assertEquals(0, page.get("totalElements").asInt());
        assertEquals(0, page.get("content").size());
    }

    @Test
    void usersOnlySeeTransactionsOfTheirOwnAccounts() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        Tokens bob = registerAndLogin("bob@example.com");
        long aliceAccount = createAccount(alice.accessToken(), "CHECKING").get("id").asLong();
        long bobAccount = createAccount(bob.accessToken(), "CHECKING").get("id").asLong();
        deposit(alice.accessToken(), aliceAccount, "10.00");
        deposit(bob.accessToken(), bobAccount, "20.00");
        deposit(bob.accessToken(), bobAccount, "30.00");

        assertEquals(1, getJson("/api/v1/transactions", alice.accessToken(), 200).get("totalElements").asInt());
        assertEquals(2, getJson("/api/v1/transactions", bob.accessToken(), 200).get("totalElements").asInt());
    }

    @Test
    void administratorsCanMonitorAllTransactions() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        Tokens bob = registerAndLogin("bob@example.com");
        Tokens admin = createAdminAndLogin("admin@example.com");
        deposit(alice.accessToken(), createAccount(alice.accessToken(), "CHECKING").get("id").asLong(), "10.00");
        deposit(bob.accessToken(), createAccount(bob.accessToken(), "CHECKING").get("id").asLong(), "20.00");

        JsonNode all = getJson("/api/v1/admin/transactions", admin.accessToken(), 200);
        assertEquals(2, all.get("totalElements").asInt());
        assertEquals(1, getJson("/api/v1/admin/transactions?type=DEPOSIT&size=1", admin.accessToken(), 200)
                .get("content").size());
        assertEquals(2, getJson("/api/v1/admin/accounts", admin.accessToken(), 200).get("totalElements").asInt());
    }
}
