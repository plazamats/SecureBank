package za.co.securebank.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.securebank.dto.response.AdminAccountResponse;
import za.co.securebank.dto.response.AdminUserResponse;
import za.co.securebank.dto.response.AuditLogResponse;
import za.co.securebank.dto.response.PageResponse;
import za.co.securebank.dto.response.TransactionResponse;
import za.co.securebank.enums.AuditAction;
import za.co.securebank.enums.TransactionType;
import za.co.securebank.security.UserPrincipal;
import za.co.securebank.service.AdminService;
import za.co.securebank.service.TransactionService;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration")
public class AdminController {

    private final AdminService adminService;
    private final TransactionService transactionService;

    @GetMapping("/users")
    @Operation(summary = "List registered users")
    public PageResponse<AdminUserResponse> users(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(adminService.listUsers(page, size));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "View one user")
    public AdminUserResponse user(@PathVariable Long userId) {
        return adminService.getUser(userId);
    }

    @PatchMapping("/users/{userId}/suspend")
    @Operation(summary = "Suspend a user and revoke all of their sessions")
    public AdminUserResponse suspend(@AuthenticationPrincipal UserPrincipal admin, @PathVariable Long userId) {
        return adminService.suspend(admin, userId);
    }

    @PatchMapping("/users/{userId}/activate")
    @Operation(summary = "Reactivate a suspended user and clear any lockout")
    public AdminUserResponse activate(@AuthenticationPrincipal UserPrincipal admin, @PathVariable Long userId) {
        return adminService.activate(admin, userId);
    }

    @GetMapping("/accounts")
    @Operation(summary = "View all bank accounts")
    public PageResponse<AdminAccountResponse> accounts(@RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(adminService.listAccounts(page, size));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Monitor all transactions")
    public PageResponse<TransactionResponse> transactions(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TransactionController.rejectInvertedRange(from, to);
        return PageResponse.from(transactionService.searchAll(accountId, type, from, to, page, size));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Review audit logs, optionally filtered by user id and action")
    public PageResponse<AuditLogResponse> auditLogs(@RequestParam(required = false) Long userId,
                                                    @RequestParam(required = false) AuditAction action,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(adminService.listAuditLogs(userId, action, page, size));
    }
}
