package za.co.securebank.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import za.co.securebank.audit.AuditService;
import za.co.securebank.dto.request.DepositRequest;
import za.co.securebank.dto.request.TransferRequest;
import za.co.securebank.dto.request.WithdrawRequest;
import za.co.securebank.dto.response.TransactionResponse;
import za.co.securebank.entity.BankAccount;
import za.co.securebank.entity.Transaction;
import za.co.securebank.entity.User;
import za.co.securebank.enums.AccountStatus;
import za.co.securebank.enums.AccountType;
import za.co.securebank.enums.AuditAction;
import za.co.securebank.enums.TransactionType;
import za.co.securebank.exception.BadRequestException;
import za.co.securebank.exception.BusinessRuleException;
import za.co.securebank.exception.InsufficientFundsException;
import za.co.securebank.exception.ResourceNotFoundException;
import za.co.securebank.mapper.TransactionMapper;
import za.co.securebank.repository.BankAccountRepository;
import za.co.securebank.repository.TransactionRepository;
import za.co.securebank.repository.UserRepository;
import za.co.securebank.security.UserPrincipal;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private BankAccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;

    private TransactionService service;
    private UserPrincipal alice;
    private User aliceEntity;
    private User bobEntity;

    @BeforeEach
    void setUp() {
        service = new TransactionService(accountRepository, transactionRepository, userRepository, auditService,
                new TransactionMapper());
        alice = new UserPrincipal(1L, "alice@example.com", "hash", List.of(), true, null, true);
        aliceEntity = User.builder().id(1L).email("alice@example.com").build();
        bobEntity = User.builder().id(2L).email("bob@example.com").build();

        lenient().when(userRepository.getReferenceById(anyLong()))
                .thenAnswer(invocation -> User.builder().id(invocation.getArgument(0)).build());
        lenient().when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private BankAccount account(long id, User owner, String number, String balance) {
        return BankAccount.builder()
                .id(id)
                .owner(owner)
                .accountNumber(number)
                .type(AccountType.CHECKING)
                .balance(new BigDecimal(balance))
                .build();
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    void depositIncreasesBalanceAndRecordsTransaction() {
        BankAccount acc = account(10L, aliceEntity, "1111111111", "100.00");
        when(accountRepository.findByIdAndOwnerIdForUpdate(10L, 1L)).thenReturn(Optional.of(acc));

        TransactionResponse response = service.deposit(alice, new DepositRequest(10L, new BigDecimal("50.25"), "Salary"));

        assertMoney("150.25", acc.getBalance());
        assertEquals(TransactionType.DEPOSIT, response.type());
        assertMoney("50.25", response.amount());
        assertMoney("150.25", response.balanceAfter());
        assertEquals("1111111111", response.destinationAccountNumber());
        assertTrue(response.reference().startsWith("TXN-"));
        verify(auditService).record(eq(AuditAction.DEPOSIT), eq(1L), eq("alice@example.com"), eq(true), startsWith("reference="));
    }

    @Test
    void depositToAnotherUsersAccountIsReportedAsNotFound() {
        when(accountRepository.findByIdAndOwnerIdForUpdate(10L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.deposit(alice, new DepositRequest(10L, new BigDecimal("10.00"), null)));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void depositRejectsZeroNegativeAndSubCentAmounts() {
        assertThrows(BadRequestException.class,
                () -> service.deposit(alice, new DepositRequest(10L, BigDecimal.ZERO, null)));
        assertThrows(BadRequestException.class,
                () -> service.deposit(alice, new DepositRequest(10L, new BigDecimal("-1.00"), null)));
        assertThrows(BadRequestException.class,
                () -> service.deposit(alice, new DepositRequest(10L, new BigDecimal("1.005"), null)));
        verifyNoInteractions(accountRepository);
    }

    @Test
    void depositToFrozenAccountIsRejected() {
        BankAccount acc = account(10L, aliceEntity, "1111111111", "100.00");
        acc.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findByIdAndOwnerIdForUpdate(10L, 1L)).thenReturn(Optional.of(acc));

        assertThrows(BusinessRuleException.class,
                () -> service.deposit(alice, new DepositRequest(10L, new BigDecimal("10.00"), null)));
        assertMoney("100.00", acc.getBalance());
    }

    @Test
    void withdrawReducesBalance() {
        BankAccount acc = account(10L, aliceEntity, "1111111111", "100.00");
        when(accountRepository.findByIdAndOwnerIdForUpdate(10L, 1L)).thenReturn(Optional.of(acc));

        TransactionResponse response = service.withdraw(alice, new WithdrawRequest(10L, new BigDecimal("40.00"), null));

        assertMoney("60.00", acc.getBalance());
        assertEquals(TransactionType.WITHDRAWAL, response.type());
        assertMoney("60.00", response.balanceAfter());
        assertEquals("1111111111", response.sourceAccountNumber());
    }

    @Test
    void withdrawingTheExactBalanceLeavesZero() {
        BankAccount acc = account(10L, aliceEntity, "1111111111", "100.00");
        when(accountRepository.findByIdAndOwnerIdForUpdate(10L, 1L)).thenReturn(Optional.of(acc));

        service.withdraw(alice, new WithdrawRequest(10L, new BigDecimal("100.00"), null));

        assertMoney("0.00", acc.getBalance());
    }

    @Test
    void withdrawBeyondBalanceIsRejectedAndBalanceIsUntouched() {
        BankAccount acc = account(10L, aliceEntity, "1111111111", "100.00");
        when(accountRepository.findByIdAndOwnerIdForUpdate(10L, 1L)).thenReturn(Optional.of(acc));

        assertThrows(InsufficientFundsException.class,
                () -> service.withdraw(alice, new WithdrawRequest(10L, new BigDecimal("100.01"), null)));

        assertMoney("100.00", acc.getBalance());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void withdrawFromAnotherUsersAccountIsReportedAsNotFound() {
        when(accountRepository.findByIdAndOwnerIdForUpdate(10L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.withdraw(alice, new WithdrawRequest(10L, new BigDecimal("10.00"), null)));
    }

    @Test
    void transferMovesFundsAtomicallyBetweenAccounts() {
        BankAccount source = account(1L, aliceEntity, "1111111111", "100.00");
        BankAccount destination = account(2L, bobEntity, "2222222222", "20.00");
        when(accountRepository.findIdByIdAndOwnerId(1L, 1L)).thenReturn(Optional.of(1L));
        when(accountRepository.findIdByAccountNumber("2222222222")).thenReturn(Optional.of(2L));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(destination));

        TransactionResponse response = service.transfer(alice,
                new TransferRequest(1L, "2222222222", new BigDecimal("30.00"), "Rent"));

        assertMoney("70.00", source.getBalance());
        assertMoney("50.00", destination.getBalance());
        assertEquals(TransactionType.TRANSFER, response.type());
        assertMoney("70.00", response.balanceAfter());
        assertEquals("1111111111", response.sourceAccountNumber());
        assertEquals("2222222222", response.destinationAccountNumber());
        verify(auditService).record(eq(AuditAction.TRANSFER), eq(1L), eq("alice@example.com"), eq(true), startsWith("reference="));
    }

    @Test
    void transferLocksAccountsInAscendingIdOrderToAvoidDeadlocks() {
        BankAccount source = account(5L, aliceEntity, "5555555555", "100.00");
        BankAccount destination = account(3L, bobEntity, "3333333333", "0.00");
        when(accountRepository.findIdByIdAndOwnerId(5L, 1L)).thenReturn(Optional.of(5L));
        when(accountRepository.findIdByAccountNumber("3333333333")).thenReturn(Optional.of(3L));
        when(accountRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(source));
        when(accountRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(destination));

        service.transfer(alice, new TransferRequest(5L, "3333333333", new BigDecimal("10.00"), null));

        InOrder order = inOrder(accountRepository);
        order.verify(accountRepository).findByIdForUpdate(3L);
        order.verify(accountRepository).findByIdForUpdate(5L);
        assertMoney("90.00", source.getBalance());
        assertMoney("10.00", destination.getBalance());
    }

    @Test
    void transferWithInsufficientFundsChangesNothing() {
        BankAccount source = account(1L, aliceEntity, "1111111111", "10.00");
        BankAccount destination = account(2L, bobEntity, "2222222222", "20.00");
        when(accountRepository.findIdByIdAndOwnerId(1L, 1L)).thenReturn(Optional.of(1L));
        when(accountRepository.findIdByAccountNumber("2222222222")).thenReturn(Optional.of(2L));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(destination));

        assertThrows(InsufficientFundsException.class, () -> service.transfer(alice,
                new TransferRequest(1L, "2222222222", new BigDecimal("10.01"), null)));

        assertMoney("10.00", source.getBalance());
        assertMoney("20.00", destination.getBalance());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transferToTheSameAccountIsRejected() {
        when(accountRepository.findIdByIdAndOwnerId(1L, 1L)).thenReturn(Optional.of(1L));
        when(accountRepository.findIdByAccountNumber("1111111111")).thenReturn(Optional.of(1L));

        assertThrows(BadRequestException.class, () -> service.transfer(alice,
                new TransferRequest(1L, "1111111111", new BigDecimal("1.00"), null)));
        verify(accountRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void transferFromAnAccountTheCallerDoesNotOwnIsReportedAsNotFound() {
        when(accountRepository.findIdByIdAndOwnerId(9L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.transfer(alice,
                new TransferRequest(9L, "2222222222", new BigDecimal("1.00"), null)));
        verify(accountRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void transferToUnknownDestinationIsReportedAsNotFound() {
        when(accountRepository.findIdByIdAndOwnerId(1L, 1L)).thenReturn(Optional.of(1L));
        when(accountRepository.findIdByAccountNumber("9999999999")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.transfer(alice,
                new TransferRequest(1L, "9999999999", new BigDecimal("1.00"), null)));
    }

    @Test
    void transferToFrozenDestinationIsRejectedAndBalancesAreUntouched() {
        BankAccount source = account(1L, aliceEntity, "1111111111", "100.00");
        BankAccount destination = account(2L, bobEntity, "2222222222", "20.00");
        destination.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findIdByIdAndOwnerId(1L, 1L)).thenReturn(Optional.of(1L));
        when(accountRepository.findIdByAccountNumber("2222222222")).thenReturn(Optional.of(2L));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(destination));

        assertThrows(BusinessRuleException.class, () -> service.transfer(alice,
                new TransferRequest(1L, "2222222222", new BigDecimal("5.00"), null)));

        assertMoney("100.00", source.getBalance());
        assertMoney("20.00", destination.getBalance());
    }

    @Test
    void receiverOfATransferNeverSeesTheSendersBalance() {
        BankAccount source = account(1L, aliceEntity, "1111111111", "100.00");
        BankAccount destination = account(2L, bobEntity, "2222222222", "20.00");
        Transaction transaction = Transaction.builder()
                .id(77L).reference("TXN-X").type(TransactionType.TRANSFER)
                .amount(new BigDecimal("30.00"))
                .sourceAccount(source).destinationAccount(destination)
                .sourceBalanceAfter(new BigDecimal("70.00")).destinationBalanceAfter(new BigDecimal("50.00"))
                .build();
        when(transactionRepository.findByIdVisibleToUser(77L, 2L)).thenReturn(Optional.of(transaction));

        UserPrincipal bob = new UserPrincipal(2L, "bob@example.com", "hash", List.of(), true, null, true);
        TransactionResponse response = service.get(bob, 77L);

        assertMoney("50.00", response.balanceAfter());
    }

    @Test
    void transactionNotVisibleToTheCallerIsReportedAsNotFound() {
        when(transactionRepository.findByIdVisibleToUser(77L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(alice, 77L));
    }

    @Test
    void searchWithoutAccountsReturnsAnEmptyPageWithoutQueryingTransactions() {
        when(accountRepository.findIdsByOwnerId(1L)).thenReturn(List.of());

        Page<TransactionResponse> page = service.search(alice, null, null, null, null, 0, 20);

        assertEquals(0, page.getTotalElements());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void searchFilteredByAnAccountOfAnotherUserIsReportedAsNotFound() {
        when(accountRepository.findIdsByOwnerId(1L)).thenReturn(List.of(1L, 2L));

        assertThrows(ResourceNotFoundException.class,
                () -> service.search(alice, 99L, null, LocalDate.now(), LocalDate.now(), 0, 20));
        verifyNoInteractions(transactionRepository);
    }
}
