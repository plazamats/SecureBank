package za.co.securebank.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.securebank.audit.AuditService;
import za.co.securebank.config.JwtProperties;
import za.co.securebank.entity.RefreshToken;
import za.co.securebank.entity.User;
import za.co.securebank.enums.AuditAction;
import za.co.securebank.enums.UserStatus;
import za.co.securebank.exception.InvalidRefreshTokenException;
import za.co.securebank.repository.RefreshTokenRepository;
import za.co.securebank.repository.UserRepository;
import za.co.securebank.util.TokenUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final String RAW = "raw-refresh-token";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;

    private RefreshTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(refreshTokenRepository, userRepository, auditService,
                new JwtProperties("unused", 15, 7, "securebank-api"));
        user = User.builder().id(1L).email("alice@example.com").status(UserStatus.ACTIVE).build();
        lenient().when(userRepository.getReferenceById(1L)).thenReturn(user);
        lenient().when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private RefreshToken storedToken(boolean revoked, Instant expiresAt) {
        return RefreshToken.builder().id(5L).tokenHash(TokenUtils.sha256Hex(RAW)).user(user)
                .revoked(revoked).expiresAt(expiresAt).build();
    }

    @Test
    void rotationRevokesTheOldTokenAndIssuesANewOne() {
        RefreshToken stored = storedToken(false, Instant.now().plus(Duration.ofDays(1)));
        when(refreshTokenRepository.findByTokenHashForUpdate(TokenUtils.sha256Hex(RAW))).thenReturn(Optional.of(stored));

        RefreshTokenService.Rotation rotation = service.rotate(RAW);

        assertTrue(stored.isRevoked());
        assertNotEquals(RAW, rotation.refreshToken());
        assertEquals(user, rotation.user());
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void onlyTheHashOfTheNewTokenIsStored() {
        RefreshToken stored = storedToken(false, Instant.now().plus(Duration.ofDays(1)));
        when(refreshTokenRepository.findByTokenHashForUpdate(TokenUtils.sha256Hex(RAW))).thenReturn(Optional.of(stored));

        String newRaw = service.rotate(RAW).refreshToken();

        org.mockito.ArgumentCaptor<RefreshToken> captor = org.mockito.ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        RefreshToken created = captor.getAllValues().get(1);
        assertEquals(TokenUtils.sha256Hex(newRaw), created.getTokenHash());
        assertNotEquals(newRaw, created.getTokenHash());
        assertEquals(64, created.getTokenHash().length());
    }

    @Test
    void reusingARevokedTokenRevokesEverySessionOfTheUser() {
        RefreshToken stored = storedToken(true, Instant.now().plus(Duration.ofDays(1)));
        when(refreshTokenRepository.findByTokenHashForUpdate(TokenUtils.sha256Hex(RAW))).thenReturn(Optional.of(stored));

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate(RAW));

        verify(refreshTokenRepository).revokeAllForUser(eq(1L), any(Instant.class));
        verify(auditService).record(eq(AuditAction.TOKEN_REUSE_DETECTED), eq(1L), eq("alice@example.com"), eq(false), anyString());
    }

    @Test
    void expiredTokenIsRejected() {
        RefreshToken stored = storedToken(false, Instant.now().minusSeconds(10));
        when(refreshTokenRepository.findByTokenHashForUpdate(TokenUtils.sha256Hex(RAW))).thenReturn(Optional.of(stored));

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate(RAW));
        assertFalse(stored.isRevoked());
    }

    @Test
    void unknownTokenIsRejected() {
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("something-else"));
    }

    @Test
    void tokenOfASuspendedUserIsRejectedAndSessionsAreRevoked() {
        user.setStatus(UserStatus.SUSPENDED);
        RefreshToken stored = storedToken(false, Instant.now().plus(Duration.ofDays(1)));
        when(refreshTokenRepository.findByTokenHashForUpdate(TokenUtils.sha256Hex(RAW))).thenReturn(Optional.of(stored));

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate(RAW));

        verify(refreshTokenRepository).revokeAllForUser(eq(1L), any(Instant.class));
        assertFalse(stored.isRevoked());
    }

    @Test
    void revokeMarksTheTokenAsRevokedAndIgnoresUnknownTokens() {
        RefreshToken stored = storedToken(false, Instant.now().plus(Duration.ofDays(1)));
        when(refreshTokenRepository.findByTokenHash(TokenUtils.sha256Hex(RAW))).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.findByTokenHash(TokenUtils.sha256Hex("unknown"))).thenReturn(Optional.empty());

        service.revoke(RAW);
        service.revoke("unknown");

        assertTrue(stored.isRevoked());
        verify(auditService).record(eq(AuditAction.LOGOUT), eq(1L), eq(null), eq(true), anyString());
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
        verify(refreshTokenRepository, never()).revokeAllForUser(any(), any());
    }
}
