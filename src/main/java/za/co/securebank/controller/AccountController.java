package za.co.securebank.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.securebank.dto.request.CreateAccountRequest;
import za.co.securebank.dto.response.AccountResponse;
import za.co.securebank.dto.response.BalanceResponse;
import za.co.securebank.security.UserPrincipal;
import za.co.securebank.service.AccountService;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
@Tag(name = "Accounts")
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @Operation(summary = "Open a new bank account for the authenticated user")
    public ResponseEntity<AccountResponse> create(@AuthenticationPrincipal UserPrincipal principal,
                                                  @Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(principal, request));
    }

    @GetMapping
    @Operation(summary = "List the accounts of the authenticated user")
    public List<AccountResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return accountService.list(principal);
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get one of your accounts")
    public AccountResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long accountId) {
        return accountService.get(principal, accountId);
    }

    @GetMapping("/{accountId}/balance")
    @Operation(summary = "Get the balance of one of your accounts")
    public BalanceResponse balance(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long accountId) {
        return accountService.balance(principal, accountId);
    }
}
