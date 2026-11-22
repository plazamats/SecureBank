package za.co.securebank.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.securebank.audit.AuditService;
import za.co.securebank.config.SecurityProperties;
import za.co.securebank.entity.User;
import za.co.securebank.enums.AuditAction;
import za.co.securebank.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;

    private LoginAttemptService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(userRepository, new SecurityProperties(3, 15, false, 24, 30, 4), auditService);
        user = User.builder().id(1L).email("alice@example.com").build();
        lenient().when(userRepository.findByEmailForUpdate("alice@example.com")).thenReturn(Optional.of(user));
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void failuresBelowTheThresholdDoNotLockTheAccount() {
        service.recordFailure("alice@example.com");
        service.recordFailure("alice@example.com");

        assertEquals(2, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
    }

    @Test
    void reachingTheThresholdLocksTheAccountForTheConfiguredPeriod() {
        service.recordFailure("alice@example.com");
        service.recordFailure("alice@example.com");
        service.recordFailure("alice@example.com");

        assertNotNull(user.getLockedUntil());
        assertTrue(user.getLockedUntil().isAfter(Instant.now().plusSeconds(14 * 60)));
        assertEquals(0, user.getFailedLoginAttempts());
        verify(auditService).record(eq(AuditAction.ACCOUNT_LOCKED), eq(1L), eq("alice@example.com"), eq(false), anyString());
    }

    @Test
    void unknownEmailIsReportedAsUnknown() {
        assertFalse(service.recordFailure("nobody@example.com"));
    }

    @Test
    void anExpiredLockStartsTheCounterFromScratch() {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(Instant.now().minusSeconds(60));

        service.recordFailure("alice@example.com");

        assertNull(user.getLockedUntil());
        assertEquals(1, user.getFailedLoginAttempts());
    }

    @Test
    void successfulLoginResetsTheCounterAndLock() {
        user.setFailedLoginAttempts(2);
        user.setLockedUntil(Instant.now().minusSeconds(5));

        service.recordSuccess(1L);

        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
    }
}
