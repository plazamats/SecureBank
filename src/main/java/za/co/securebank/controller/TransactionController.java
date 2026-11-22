package za.co.securebank.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.securebank.dto.request.DepositRequest;
import za.co.securebank.dto.request.TransferRequest;
import za.co.securebank.dto.request.WithdrawRequest;
import za.co.securebank.dto.response.PageResponse;
import za.co.securebank.dto.response.TransactionResponse;
import za.co.securebank.enums.TransactionType;
import za.co.securebank.security.UserPrincipal;
import za.co.securebank.service.TransactionService;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
@Tag(name = "Transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/deposit")
    @Operation(summary = "Deposit funds into one of your accounts")
    public ResponseEntity<TransactionResponse> deposit(@AuthenticationPrincipal UserPrincipal principal,
                                                       @Valid @RequestBody DepositRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.deposit(principal, request));
    }

    @PostMapping("/withdraw")
    @Operation(summary = "Withdraw funds from one of your accounts")
    public ResponseEntity<TransactionResponse> withdraw(@AuthenticationPrincipal UserPrincipal principal,
                                                        @Valid @RequestBody WithdrawRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.withdraw(principal, request));
    }

    @PostMapping("/transfer")
    @Operation(summary = "Transfer funds from one of your accounts to any account number")
    public ResponseEntity<TransactionResponse> transfer(@AuthenticationPrincipal UserPrincipal principal,
                                                        @Valid @RequestBody TransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.transfer(principal, request));
    }

    @GetMapping
    @Operation(summary = "Transaction history with optional account, type and date filters (dates are yyyy-MM-dd)")
    public PageResponse<TransactionResponse> history(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        rejectInvertedRange(from, to);
        return PageResponse.from(transactionService.search(principal, accountId, type, from, to, page, size));
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get one transaction that involves one of your accounts")
    public TransactionResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long transactionId) {
        return transactionService.get(principal, transactionId);
    }

    static void rejectInvertedRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new za.co.securebank.exception.BadRequestException("'from' must not be after 'to'");
        }
    }
}
