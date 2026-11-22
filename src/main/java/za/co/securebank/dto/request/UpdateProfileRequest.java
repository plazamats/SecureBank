package za.co.securebank.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Pattern(regexp = "^\\+?[0-9 ]{7,20}$", message = "Phone number must contain 7 to 20 digits and may start with +")
        String phone) {
}
