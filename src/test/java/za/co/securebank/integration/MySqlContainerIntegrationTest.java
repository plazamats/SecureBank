package za.co.securebank.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import za.co.securebank.dto.request.DepositRequest;
import za.co.securebank.dto.request.WithdrawRequest;
import za.co.securebank.exception.InsufficientFundsException;
import za.co.securebank.security.UserPrincipal;
import za.co.securebank.service.TransactionService;

/**
 * Runs the real Flyway migrations against a real MySQL 8 server started by Testcontainers. The class is skipped
 * automatically when Docker is not available. See the README for the H2 based alternative.
 */
@Testcontainers(disabledWithoutDocker = true)
class MySqlContainerIntegrationTest extends AbstractIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionService transactionService;

    @Test
    void flywayMigrationsAreAppliedToMySql() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = 1", Integer.class);
        assertEquals(2, applied);
        Integer roles = jdbcTemplate.queryForObject("select count(*) from roles", Integer.class);
        assertEquals(2, roles);
    }

    @Test
    void databaseConstraintsProtectFinancialIntegrity() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        long accountId = createAccount(alice.accessToken(), "CHECKING").get("id").asLong();

        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("update bank_accounts set balance = -1.00 where id = ?", accountId));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into transactions (reference, type, status, amount, destination_account_id, initiated_by, created_at) "
                        + "values ('TXN-BAD', 'DEPOSIT', 'COMPLETED', -5.00, ?, ?, now(6))",
                accountId, userId("alice@example.com")));
    }

    @Test
    void fullBankingFlowWorksOnMySql() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        Tokens bob = registerAndLogin("bob@example.com");
        JsonNode aliceAccount = createAccount(alice.accessToken(), "CHECKING");
        JsonNode bobAccount = createAccount(bob.accessToken(), "SAVINGS");
        long aliceId = aliceAccount.get("id").asLong();
        deposit(alice.accessToken(), aliceId, "100.00");

        postJson("/api/v1/transactions/withdraw", Map.of("accountId", aliceId, "amount", new BigDecimal("150.00")),
                alice.accessToken(), 422);
        postJson("/api/v1/transactions/transfer", Map.of("sourceAccountId", aliceId,
                "destinationAccountNumber", bobAccount.get("accountNumber").asText(),
                "amount", new BigDecimal("40.00")), alice.accessToken(), 201);

        assertEquals(0, new BigDecimal("60.00").compareTo(balanceOf(alice.accessToken(), aliceId)));
        assertEquals(0, new BigDecimal("40.00").compareTo(balanceOf(bob.accessToken(), bobAccount.get("id").asLong())));
        assertEquals(2, getJson("/api/v1/transactions", alice.accessToken(), 200).get("totalElements").asInt());
    }

    @Test
    void parallelWithdrawalsCannotOverdrawOnMySql() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        long accountId = createAccount(alice.accessToken(), "CHECKING").get("id").asLong();
        UserPrincipal principal = UserPrincipal.from(userRepository.findByEmail("alice@example.com").orElseThrow());
        transactionService.deposit(principal, new DepositRequest(accountId, new BigDecimal("100.00"), null));

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(executor.submit(() -> {
                start.await();
                try {
                    transactionService.withdraw(principal, new WithdrawRequest(accountId, new BigDecimal("30.00"), null));
                    return true;
                } catch (InsufficientFundsException ex) {
                    return false;
                }
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get(60, TimeUnit.SECONDS)) {
                succeeded++;
            }
        }
        executor.shutdownNow();

        assertEquals(3, succeeded);
        assertTrue(new BigDecimal("10.00").compareTo(balanceOf(alice.accessToken(), accountId)) == 0);
    }
}
