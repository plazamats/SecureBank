package za.co.securebank.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import za.co.securebank.exception.BadRequestException;

public final class MoneyUtils {

    private MoneyUtils() {
    }

    /**
     * Validates a monetary amount and returns it with exactly two decimal places.
     * Amounts must be positive and must not carry more than two decimal places (no silent rounding of money).
     */
    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Amount must be greater than zero");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new BadRequestException("Amount must not have more than 2 decimal places");
        }
    }
}
