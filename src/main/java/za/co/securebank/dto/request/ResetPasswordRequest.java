package za.co.securebank.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import za.co.securebank.validation.StrongPassword;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 200) String token,
        @NotBlank @StrongPassword String newPassword) {
}
