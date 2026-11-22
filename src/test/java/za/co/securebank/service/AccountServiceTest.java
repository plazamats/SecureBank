package za.co.securebank.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.securebank.audit.AuditService;
import za.co.securebank.dto.request.CreateAccountRequest;
import za.co.securebank.dto.response.AccountResponse;
import za.co.securebank.dto.response.BalanceResponse;
import za.co.securebank.entity.BankAccount;
import za.co.securebank.entity.User;
import za.co.securebank.enums.AccountType;
import za.co.securebank.enums.AuditAction;
import za.co.securebank.exception.BusinessRuleException;
import za.co.securebank.exception.ResourceNotFoundException;
import za.co.securebank.mapper.AccountMapper;
import za.co.securebank.repository.BankAccountRepository;
import za.co.securebank.repository.UserRepository;
import za.co.securebank.security.UserPrincipal;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private BankAccountRepository accountRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;

    private AccountService service;
    private UserPrincipal alice;

    @BeforeEach
    void setUp() {
        service = new AccountService(accountRepository, userRepository, auditService, new AccountMapper(), 5);
        alice = new UserPrincipal(1L, "alice@example.com", "hash", List.of(), true, null, true);
    }

    @Test
    void createsAnAccountOwnedByTheAuthenticatedUserWithZeroBalance() {
        when(accountRepository.countByOwnerId(1L)).thenReturn(0L);
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(userRepository.getReferenceById(1L)).thenReturn(User.builder().id(1L).build());
        when(accountRepository.save(any(BankAccount.class))).thenAnswer(invocation -> {
            BankAccount saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        AccountResponse response = service.create(alice, new CreateAccountRequest(AccountType.SAVINGS, "  Holiday  "));

        assertEquals(11L, response.id());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.balance()));
        assertEquals("Holiday", response.name());
        assertEquals("ZAR", response.currency());
        assertTrue(response.accountNumber().matches("\\d{10}"));
        verify(auditService).record(eq(AuditAction.ACCOUNT_CREATED), eq(1L), eq("alice@example.com"), eq(true), anyString());
    }

    @Test
    void refusesToCreateMoreAccountsThanTheConfiguredLimit() {
        when(accountRepository.countByOwnerId(1L)).thenReturn(5L);

        assertThrows(BusinessRuleException.class,
                () -> service.create(alice, new CreateAccountRequest(AccountType.CHECKING, null)));
        verify(accountRepository, never()).save(any(BankAccount.class));
    }

    @Test
    void getReturnsOwnAccount() {
        BankAccount account = BankAccount.builder().id(3L).accountNumber("1234567890").type(AccountType.CHECKING)
                .balance(new BigDecimal("12.34")).build();
        when(accountRepository.findByIdAndOwnerId(3L, 1L)).thenReturn(Optional.of(account));

        assertEquals("1234567890", service.get(alice, 3L).accountNumber());
        BalanceResponse balance = service.balance(alice, 3L);
        assertEquals(0, new BigDecimal("12.34").compareTo(balance.balance()));
    }

    @Test
    void accountOfAnotherUserIsReportedAsNotFound() {
        when(accountRepository.findByIdAndOwnerId(3L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(alice, 3L));
        assertThrows(ResourceNotFoundException.class, () -> service.balance(alice, 3L));
    }
}
