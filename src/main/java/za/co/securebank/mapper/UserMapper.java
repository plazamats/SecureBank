package za.co.securebank.mapper;

import java.util.List;
import org.springframework.stereotype.Component;
import za.co.securebank.dto.response.AdminUserResponse;
import za.co.securebank.dto.response.UserResponse;
import za.co.securebank.entity.User;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.getPhone(),
                user.getStatus(), user.isEmailVerified(), roleNames(user), user.getCreatedAt());
    }

    public AdminUserResponse toAdminResponse(User user) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getPhone(), user.getStatus(), user.isEmailVerified(), user.getFailedLoginAttempts(),
                user.getLockedUntil(), roleNames(user), user.getCreatedAt(), user.getUpdatedAt());
    }

    private List<String> roleNames(User user) {
        return user.getRoles().stream().map(role -> role.getName().name()).sorted().toList();
    }
}
