package za.co.securebank.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.securebank.audit.AuditService;
import za.co.securebank.dto.request.DepositRequest;
import za.co.securebank.dto.request.TransferRequest;
import za.co.securebank.dto.request.WithdrawRequest;
import za.co.securebank.dto.response.TransactionResponse;
import za.co.securebank.entity.BankAccount;
import za.co.securebank.entity.Transaction;
import za.co.securebank.enums.AccountStatus;
import za.co.securebank.enums.AuditAction;
import za.co.securebank.enums.TransactionStatus;
import za.co.securebank.enums.TransactionType;
import za.co.securebank.exception.BadRequestException;
import za.co.securebank.exception.BusinessRuleException;
import za.co.securebank.exception.InsufficientFundsException;
import za.co.securebank.exception.ResourceNotFoundException;
import za.co.securebank.mapper.TransactionMapper;
import za.co.securebank.repository.BankAccountRepository;
import za.co.securebank.repository.TransactionRepository;
import za.co.securebank.repository.TransactionSpecifications;
import za.co.securebank.repository.UserRepository;
import za.co.securebank.security.UserPrincipal;
import za.co.securebank.util.MaskUtils;
import za.co.securebank.util.MoneyUtils;
import za.co.securebank.util.PagingUtils;

/**
 * Financial operations. Every balance change runs in one database transaction and takes a pessimistic row lock
 * (SELECT ... FOR UPDATE) on each account involved, so concurrent requests are serialized per account and a balance
 * can never be spent twice. Transfers lock both accounts in ascending id order, which rules out deadlocks between
 * opposite transfers. A version column on the account adds a second line of defence.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    /** Day boundaries of the date filters are interpreted in South African time. */
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Africa/Johannesburg");

    private final BankAccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final TransactionMapper transactionMapper;

    @Transactional
    public TransactionResponse deposit(UserPrincipal principal, DepositRequest request) {
        BigDecimal amount = MoneyUtils.normalize(request.amount());
        BankAccount account = accountRepository.findByIdAndOwnerIdForUpdate(request.accountId(), principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        requireActive(account, "Account");

        account.setBalance(account.getBalance().add(amount));
        Transaction transaction = Transaction.builder()
                .reference(newReference())
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .amount(amount)
                .destinationAccount(account)
                .destinationBalanceAfter(account.getBalance())
                .description(cleanDescription(request.description()))
                .initiatedBy(userRepository.getReferenceById(principal.getId()))
                .build();
        return saveAndAudit(principal, transaction, AuditAction.DEPOSIT);
    }

    @Transactional
    public TransactionResponse withdraw(UserPrincipal principal, WithdrawRequest request) {
        BigDecimal amount = MoneyUtils.normalize(request.amount());
        BankAccount account = accountRepository.findByIdAndOwnerIdForUpdate(request.accountId(), principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        requireActive(account, "Account");
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }

        account.setBalance(account.getBalance().subtract(amount));
        Transaction transaction = Transaction.builder()
                .reference(newReference())
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.COMPLETED)
                .amount(amount)
                .sourceAccount(account)
                .sourceBalanceAfter(account.getBalance())
                .description(cleanDescription(request.description()))
                .initiatedBy(userRepository.getReferenceById(principal.getId()))
                .build();
        return saveAndAudit(principal, transaction, AuditAction.WITHDRAWAL);
    }

    @Transactional
    public TransactionResponse transfer(UserPrincipal principal, TransferRequest request) {
        BigDecimal amount = MoneyUtils.normalize(request.amount());

        Long sourceId = accountRepository.findIdByIdAndOwnerId(request.sourceAccountId(), principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));
        Long destinationId = accountRepository.findIdByAccountNumber(request.destinationAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));
        if (sourceId.equals(destinationId)) {
            throw new BadRequestException("Source and destination accounts must be different");
        }

        Long firstId = sourceId.compareTo(destinationId) < 0 ? sourceId : destinationId;
        Long secondId = firstId.equals(sourceId) ? destinationId : sourceId;
        BankAccount first = lockAccount(firstId);
        BankAccount second = lockAccount(secondId);
        BankAccount source = firstId.equals(sourceId) ? first : second;
        BankAccount destination = firstId.equals(sourceId) ? second : first;

        requireActive(source, "Source account");
        requireActive(destination, "Destination account");
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new BusinessRuleException("Transfers between different currencies are not supported");
        }
        if (source.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }

        source.setBalance(source.getBalance().subtract(amount));
        destination.setBalance(destination.getBalance().add(amount));
        Transaction transaction = Transaction.builder()
                .reference(newReference())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .amount(amount)
                .sourceAccount(source)
                .destinationAccount(destination)
                .sourceBalanceAfter(source.getBalance())
                .destinationBalanceAfter(destination.getBalance())
                .description(cleanDescription(request.description()))
                .initiatedBy(userRepository.getReferenceById(principal.getId()))
                .build();
        return saveAndAudit(principal, transaction, AuditAction.TRANSFER);
    }

    /** Transaction history of the caller's own accounts, optionally filtered by account, type and date range. */
    @Transactional(readOnly = true)
    public Page<TransactionResponse> search(UserPrincipal principal, Long accountId, TransactionType type,
                                            LocalDate from, LocalDate to, int page, int size) {
        Pageable pageable = PagingUtils.newestFirst(page, size);
        List<Long> ownAccountIds = accountRepository.findIdsByOwnerId(principal.getId());
        if (accountId != null && !ownAccountIds.contains(accountId)) {
            throw new ResourceNotFoundException("Account not found");
        }
        if (ownAccountIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        Page<Transaction> result = transactionRepository.findAll(
                TransactionSpecifications.filter(ownAccountIds, accountId, type, startOf(from), endOf(to)), pageable);
        return result.map(t -> transactionMapper.toResponse(t, principal.getId()));
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(UserPrincipal principal, Long transactionId) {
        Transaction transaction = transactionRepository.findByIdVisibleToUser(transactionId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        return transactionMapper.toResponse(transaction, principal.getId());
    }

    /** Administrator view over all transactions. Access is enforced by the controller and the URL rules. */
    @Transactional(readOnly = true)
    public Page<TransactionResponse> searchAll(Long accountId, TransactionType type, LocalDate from, LocalDate to,
                                               int page, int size) {
        Pageable pageable = PagingUtils.newestFirst(page, size);
        Page<Transaction> result = transactionRepository.findAll(
                TransactionSpecifications.filter(null, accountId, type, startOf(from), endOf(to)), pageable);
        return result.map(t -> transactionMapper.toResponse(t, null));
    }

    private TransactionResponse saveAndAudit(UserPrincipal principal, Transaction transaction, AuditAction action) {
        Transaction saved = transactionRepository.save(transaction);
        auditService.record(action, principal.getId(), principal.getEmail(), true, "reference=" + saved.getReference());
        return transactionMapper.toResponse(saved, principal.getId());
    }

    private BankAccount lockAccount(Long id) {
        return accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private static void requireActive(BankAccount account, String label) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException(label + " " + MaskUtils.maskAccountNumber(account.getAccountNumber())
                    + " is not active");
        }
    }

    private static String newReference() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    private static String cleanDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    private static Instant startOf(LocalDate from) {
        return from == null ? null : from.atStartOfDay(BUSINESS_ZONE).toInstant();
    }

    private static Instant endOf(LocalDate to) {
        return to == null ? null : to.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
    }
}
