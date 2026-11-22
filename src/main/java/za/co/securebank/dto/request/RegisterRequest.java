package za.co.securebank.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import za.co.securebank.validation.StrongPassword;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @StrongPassword String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Pattern(regexp = "^\\+?[0-9 ]{7,20}$", message = "Phone number must contain 7 to 20 digits and may start with +")
        String phone) {
}
