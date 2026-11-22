package za.co.securebank.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import za.co.securebank.entity.User;

/**
 * Development implementation of {@link EmailService}. Tokens are only written to the log when
 * securebank.mail.log-tokens is true (enabled in the dev profile, disabled elsewhere).
 */
@Slf4j
@Service
public class LoggingEmailService implements EmailService {

    private final boolean logTokens;

    public LoggingEmailService(@Value("${securebank.mail.log-tokens:false}") boolean logTokens) {
        this.logTokens = logTokens;
    }

    @Override
    public void sendVerificationEmail(User user, String rawToken) {
        log.info("Verification email queued for userId={}", user.getId());
        if (logTokens) {
            log.debug("DEV ONLY verification token for userId={}: {}", user.getId(), rawToken);
        }
    }

    @Override
    public void sendPasswordResetEmail(User user, String rawToken) {
        log.info("Password reset email queued for userId={}", user.getId());
        if (logTokens) {
            log.debug("DEV ONLY password reset token for userId={}: {}", user.getId(), rawToken);
        }
    }
}
