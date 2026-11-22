package za.co.securebank.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.securebank.dto.request.DepositRequest;
import za.co.securebank.dto.request.TransferRequest;
import za.co.securebank.dto.request.WithdrawRequest;
import za.co.securebank.entity.BankAccount;
import za.co.securebank.exception.InsufficientFundsException;
import za.co.securebank.security.UserPrincipal;
import za.co.securebank.service.TransactionService;

/**
 * Proves that the locking strategy prevents double spending and deadlocks. The tasks call the service layer
 * directly from many threads, each in its own database transaction.
 */
class ConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    private UserPrincipal principal(String email) {
        return UserPrincipal.from(userRepository.findByEmail(email).orElseThrow());
    }

    private BigDecimal balance(long accountId) {
        return accountRepository.findById(accountId).map(BankAccount::getBalance).orElseThrow();
    }

    private List<Throwable> runConcurrently(List<Callable<Void>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    task.call();
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            }));
        }
        ready.await(30, TimeUnit.SECONDS);
        start.countDown();
        List<Throwable> failures = new ArrayList<>();
        for (Future<Throwable> future : futures) {
            Throwable result = future.get(90, TimeUnit.SECONDS);
            if (result != null) {
                failures.add(result);
            }
        }
        executor.shutdownNow();
        return failures;
    }

    @Test
    void parallelWithdrawalsCanNeverSpendMoreThanTheBalance() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        JsonNode account = createAccount(alice.accessToken(), "CHECKING");
        long accountId = account.get("id").asLong();
        UserPrincipal aliceUser = principal("alice@example.com");
        transactionService.deposit(aliceUser, new DepositRequest(accountId, new BigDecimal("100.00"), null));

        AtomicInteger succeeded = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> {
                transactionService.withdraw(aliceUser, new WithdrawRequest(accountId, new BigDecimal("30.00"), null));
                succeeded.incrementAndGet();
                return null;
            });
        }

        List<Throwable> failures = runConcurrently(tasks);

        assertTrue(failures.stream().allMatch(t -> t instanceof InsufficientFundsException),
                "unexpected failures: " + failures);
        assertEquals(3, succeeded.get());
        assertEquals(7, failures.size());
        assertEquals(0, new BigDecimal("10.00").compareTo(balance(accountId)));
        assertEquals(4, transactionRepository.count());
    }

    @Test
    void oppositeTransfersConserveMoneyAndDoNotDeadlock() throws Exception {
        Tokens alice = registerAndLogin("alice@example.com");
        Tokens bob = registerAndLogin("bob@example.com");
        JsonNode aliceAccount = createAccount(alice.accessToken(), "CHECKING");
        JsonNode bobAccount = createAccount(bob.accessToken(), "CHECKING");
        long aliceId = aliceAccount.get("id").asLong();
        long bobId = bobAccount.get("id").asLong();
        UserPrincipal aliceUser = principal("alice@example.com");
        UserPrincipal bobUser = principal("bob@example.com");
        transactionService.deposit(aliceUser, new DepositRequest(aliceId, new BigDecimal("500.00"), null));
        transactionService.deposit(bobUser, new DepositRequest(bobId, new BigDecimal("500.00"), null));

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> {
                transactionService.transfer(aliceUser, new TransferRequest(aliceId,
                        bobAccount.get("accountNumber").asText(), new BigDecimal("10.00"), null));
                return null;
            });
            tasks.add(() -> {
                transactionService.transfer(bobUser, new TransferRequest(bobId,
                        aliceAccount.get("accountNumber").asText(), new BigDecimal("10.00"), null));
                return null;
            });
        }

        List<Throwable> failures = runConcurrently(tasks);

        assertTrue(failures.isEmpty(), "unexpected failures: " + failures);
        assertEquals(0, new BigDecimal("500.00").compareTo(balance(aliceId)));
        assertEquals(0, new BigDecimal("500.00").compareTo(balance(bobId)));
        assertEquals(22, transactionRepository.count());
    }
}
