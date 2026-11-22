package za.co.securebank.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.securebank.enums.AccountType;

public record CreateAccountRequest(
        @NotNull AccountType type,
        @Size(max = 100) String name) {
}
