package za.co.securebank.service;

import za.co.securebank.entity.User;

/**
 * Outbound email abstraction. The default implementation only logs. To send real email, add a bean implementing
 * this interface (for example with spring-boot-starter-mail) and remove {@link LoggingEmailService}.
 */
public interface EmailService {

    void sendVerificationEmail(User user, String rawToken);

    void sendPasswordResetEmail(User user, String rawToken);
}
