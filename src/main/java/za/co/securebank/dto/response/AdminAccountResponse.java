package za.co.securebank.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import za.co.securebank.enums.AccountStatus;
import za.co.securebank.enums.AccountType;

public record AdminAccountResponse(
        Long id,
        String accountNumber,
        Long ownerId,
        String ownerEmail,
        AccountType type,
        String name,
        BigDecimal balance,
        String currency,
        AccountStatus status,
        Instant createdAt) {
}
