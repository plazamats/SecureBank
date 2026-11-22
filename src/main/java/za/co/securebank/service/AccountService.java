package za.co.securebank.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.securebank.audit.AuditService;
import za.co.securebank.dto.request.CreateAccountRequest;
import za.co.securebank.dto.response.AccountResponse;
import za.co.securebank.dto.response.BalanceResponse;
import za.co.securebank.entity.BankAccount;
import za.co.securebank.enums.AuditAction;
import za.co.securebank.exception.BusinessRuleException;
import za.co.securebank.exception.ResourceNotFoundException;
import za.co.securebank.mapper.AccountMapper;
import za.co.securebank.repository.BankAccountRepository;
import za.co.securebank.repository.UserRepository;
import za.co.securebank.security.UserPrincipal;
import za.co.securebank.util.MaskUtils;

/**
 * Account use cases. The owner is always the authenticated principal; no client supplied user id is ever used.
 * Accounts that belong to somebody else are reported as "not found" so their existence is not disclosed.
 */
@Service
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_NUMBER_ATTEMPTS = 10;

    private final BankAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AccountMapper accountMapper;
    private final int maxAccountsPerUser;

    public AccountService(BankAccountRepository accountRepository, UserRepository userRepository,
                          AuditService auditService, AccountMapper accountMapper,
                          @Value("${securebank.accounts.max-per-user:5}") int maxAccountsPerUser) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.accountMapper = accountMapper;
        this.maxAccountsPerUser = maxAccountsPerUser;
    }

    @Transactional
    public AccountResponse create(UserPrincipal principal, CreateAccountRequest request) {
        if (accountRepository.countByOwnerId(principal.getId()) >= maxAccountsPerUser) {
            throw new BusinessRuleException("The maximum number of accounts (" + maxAccountsPerUser + ") has been reached");
        }
        BankAccount account = BankAccount.builder()
                .accountNumber(generateAccountNumber())
                .owner(userRepository.getReferenceById(principal.getId()))
                .type(request.type())
                .name(request.name() == null || request.name().isBlank() ? null : request.name().trim())
                .build();
        account = accountRepository.save(account);
        auditService.record(AuditAction.ACCOUNT_CREATED, principal.getId(), principal.getEmail(), true,
                "Account created: " + MaskUtils.maskAccountNumber(account.getAccountNumber()));
        return accountMapper.toResponse(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(UserPrincipal principal) {
        return accountRepository.findByOwnerIdOrderByCreatedAtDesc(principal.getId()).stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UserPrincipal principal, Long accountId) {
        return accountMapper.toResponse(findOwned(principal, accountId));
    }

    @Transactional(readOnly = true)
    public BalanceResponse balance(UserPrincipal principal, Long accountId) {
        BankAccount account = findOwned(principal, accountId);
        return new BalanceResponse(account.getId(), account.getAccountNumber(), account.getBalance(),
                account.getCurrency(), Instant.now());
    }

    private BankAccount findOwned(UserPrincipal principal, Long accountId) {
        return accountRepository.findByIdAndOwnerId(accountId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private String generateAccountNumber() {
        for (int attempt = 0; attempt < MAX_NUMBER_ATTEMPTS; attempt++) {
            String candidate = String.valueOf(RANDOM.nextLong(1_000_000_000L, 10_000_000_000L));
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to allocate a unique account number");
    }
}
