package za.co.securebank.mapper;

import org.springframework.stereotype.Component;
import za.co.securebank.dto.response.AccountResponse;
import za.co.securebank.dto.response.AdminAccountResponse;
import za.co.securebank.entity.BankAccount;

@Component
public class AccountMapper {

    public AccountResponse toResponse(BankAccount account) {
        return new AccountResponse(account.getId(), account.getAccountNumber(), account.getType(), account.getName(),
                account.getBalance(), account.getCurrency(), account.getStatus(), account.getCreatedAt());
    }

    public AdminAccountResponse toAdminResponse(BankAccount account) {
        return new AdminAccountResponse(account.getId(), account.getAccountNumber(), account.getOwner().getId(),
                account.getOwner().getEmail(), account.getType(), account.getName(), account.getBalance(),
                account.getCurrency(), account.getStatus(), account.getCreatedAt());
    }
}
