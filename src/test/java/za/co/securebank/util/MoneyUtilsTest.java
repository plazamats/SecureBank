package za.co.securebank.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import za.co.securebank.exception.BadRequestException;

class MoneyUtilsTest {

    @Test
    void normalizesToTwoDecimalPlaces() {
        assertEquals(new BigDecimal("10.00"), MoneyUtils.normalize(new BigDecimal("10")));
        assertEquals(new BigDecimal("10.50"), MoneyUtils.normalize(new BigDecimal("10.5")));
        assertEquals(new BigDecimal("0.01"), MoneyUtils.normalize(new BigDecimal("0.01")));
    }

    @Test
    void rejectsZeroNegativeAndNullAmounts() {
        assertThrows(BadRequestException.class, () -> MoneyUtils.normalize(BigDecimal.ZERO));
        assertThrows(BadRequestException.class, () -> MoneyUtils.normalize(new BigDecimal("-5.00")));
        assertThrows(BadRequestException.class, () -> MoneyUtils.normalize(null));
    }

    @Test
    void rejectsMoreThanTwoDecimalPlacesInsteadOfRounding() {
        assertThrows(BadRequestException.class, () -> MoneyUtils.normalize(new BigDecimal("10.001")));
    }

    @Test
    void masksAccountNumbers() {
        assertEquals("******7890", MaskUtils.maskAccountNumber("1234567890"));
        assertEquals("****", MaskUtils.maskAccountNumber("12"));
        assertEquals("****", MaskUtils.maskAccountNumber(null));
    }
}
