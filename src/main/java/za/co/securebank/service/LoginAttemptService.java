package za.co.securebank.service;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.securebank.audit.AuditService;
import za.co.securebank.config.SecurityProperties;
import za.co.securebank.entity.User;
import za.co.securebank.enums.AuditAction;
import za.co.securebank.repository.UserRepository;

/** Tracks failed logins and locks accounts temporarily after too many consecutive failures. */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final SecurityProperties securityProperties;
    private final AuditService auditService;

    /**
     * Records a failed login for the given (normalized) email.
     *
     * @return true when an account with that email exists
     */
    @Transactional
    public boolean recordFailure(String email) {
        return userRepository.findByEmailForUpdate(email).map(user -> {
            Instant now = Instant.now();
            if (user.getLockedUntil() != null && user.getLockedUntil().isBefore(now)) {
                user.setLockedUntil(null);
                user.setFailedLoginAttempts(0);
            }
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            auditService.record(AuditAction.LOGIN_FAILURE, user.getId(), user.getEmail(), false,
                    "Invalid credentials, consecutive failures: " + attempts);
            if (attempts >= securityProperties.maxFailedAttempts()) {
                user.setLockedUntil(now.plus(Duration.ofMinutes(securityProperties.lockoutMinutes())));
                user.setFailedLoginAttempts(0);
                auditService.record(AuditAction.ACCOUNT_LOCKED, user.getId(), user.getEmail(), false,
                        "Account locked for " + securityProperties.lockoutMinutes() + " minutes");
            }
            userRepository.save(user);
            return true;
        }).orElse(false);
    }

    @Transactional
    public void recordSuccess(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
                userRepository.save(user);
            }
        });
    }
}
