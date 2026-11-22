package za.co.securebank.dto.response;

import java.time.Instant;
import java.util.List;
import za.co.securebank.enums.UserStatus;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        UserStatus status,
        boolean emailVerified,
        List<String> roles,
        Instant createdAt) {
}
