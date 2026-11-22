package za.co.securebank.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record BalanceResponse(
        Long accountId,
        String accountNumber,
        BigDecimal balance,
        String currency,
        Instant asOf) {
}
