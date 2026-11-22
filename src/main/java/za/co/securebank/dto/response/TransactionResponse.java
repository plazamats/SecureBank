package za.co.securebank.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import za.co.securebank.enums.TransactionStatus;
import za.co.securebank.enums.TransactionType;

public record TransactionResponse(
        Long id,
        String reference,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        String currency,
        String sourceAccountNumber,
        String destinationAccountNumber,
        BigDecimal balanceAfter,
        String description,
        Instant createdAt) {
}
