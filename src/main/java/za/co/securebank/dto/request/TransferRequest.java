package za.co.securebank.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record TransferRequest(
        @NotNull Long sourceAccountId,
        @NotBlank @Pattern(regexp = "^\\d{10}$", message = "Destination account number must be exactly 10 digits")
        String destinationAccountNumber,
        @NotNull @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 15, fraction = 2, message = "Amount must have at most 15 integer digits and 2 decimal places")
        BigDecimal amount,
        @Size(max = 255) String description) {
}
