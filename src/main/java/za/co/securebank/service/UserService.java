package za.co.securebank.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.securebank.audit.AuditService;
import za.co.securebank.dto.request.UpdateProfileRequest;
import za.co.securebank.dto.response.UserResponse;
import za.co.securebank.entity.User;
import za.co.securebank.enums.AuditAction;
import za.co.securebank.exception.ResourceNotFoundException;
import za.co.securebank.mapper.UserMapper;
import za.co.securebank.repository.UserRepository;
import za.co.securebank.security.UserPrincipal;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserResponse getProfile(UserPrincipal principal) {
        return userMapper.toResponse(load(principal));
    }

    @Transactional
    public UserResponse updateProfile(UserPrincipal principal, UpdateProfileRequest request) {
        User user = load(principal);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        user = userRepository.save(user);
        auditService.record(AuditAction.PROFILE_UPDATED, user.getId(), user.getEmail(), true, "Profile updated");
        return userMapper.toResponse(user);
    }

    private User load(UserPrincipal principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
