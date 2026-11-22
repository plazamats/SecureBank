package za.co.securebank.mapper;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import za.co.securebank.dto.response.TransactionResponse;
import za.co.securebank.entity.BankAccount;
import za.co.securebank.entity.Transaction;

@Component
public class TransactionMapper {

    /**
     * Maps a transaction for a given viewer. The running balance is only revealed for an account the viewer owns,
     * so the receiver of a transfer never sees the sender's balance. A null viewer (administrator) sees the
     * balance of the primary account.
     */
    public TransactionResponse toResponse(Transaction transaction, Long viewerUserId) {
        BankAccount source = transaction.getSourceAccount();
        BankAccount destination = transaction.getDestinationAccount();

        BigDecimal balanceAfter;
        if (viewerUserId == null) {
            balanceAfter = source != null ? transaction.getSourceBalanceAfter() : transaction.getDestinationBalanceAfter();
        } else if (source != null && viewerUserId.equals(source.getOwner().getId())) {
            balanceAfter = transaction.getSourceBalanceAfter();
        } else if (destination != null && viewerUserId.equals(destination.getOwner().getId())) {
            balanceAfter = transaction.getDestinationBalanceAfter();
        } else {
            balanceAfter = null;
        }

        String currency = source != null ? source.getCurrency() : destination.getCurrency();
        return new TransactionResponse(transaction.getId(), transaction.getReference(), transaction.getType(),
                transaction.getStatus(), transaction.getAmount(), currency,
                source != null ? source.getAccountNumber() : null,
                destination != null ? destination.getAccountNumber() : null,
                balanceAfter, transaction.getDescription(), transaction.getCreatedAt());
    }
}
