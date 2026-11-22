package za.co.securebank.service;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

/**
 * Refresh token lifecycle: issue, rotate (one time use), revoke. Only SHA-256 hashes are stored. Presenting a token
 * that was already used signals theft, so every session of that user is revoked.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    public record Rotation(String refreshToken, User user) {
    }

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final JwtProperties jwtProperties;

    @Transactional
    public String issue(Long userId) {
        String raw = TokenUtils.generateToken();
        RefreshToken token = RefreshToken.builder()
                .tokenHash(TokenUtils.sha256Hex(raw))
                .user(userRepository.getReferenceById(userId))
                .expiresAt(Instant.now().plus(Duration.ofDays(jwtProperties.refreshTokenDays())))
                .build();
        refreshTokenRepository.save(token);
        return raw;
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHashForUpdate(TokenUtils.sha256Hex(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        User user = stored.getUser();
        Instant now = Instant.now();

        if (stored.isRevoked()) {
            refreshTokenRepository.revokeAllForUser(user.getId(), now);
            auditService.record(AuditAction.TOKEN_REUSE_DETECTED, user.getId(), user.getEmail(), false,
                    "A revoked refresh token was presented; all sessions were revoked");
            throw new InvalidRefreshTokenException();
        }
        if (stored.getExpiresAt().isBefore(now)) {
            throw new InvalidRefreshTokenException();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            refreshTokenRepository.revokeAllForUser(user.getId(), now);
            throw new InvalidRefreshTokenException();
        }

        stored.setRevoked(true);
        stored.setRevokedAt(now);
        refreshTokenRepository.save(stored);

        String newRaw = issue(user.getId());
        auditService.record(AuditAction.TOKEN_REFRESHED, user.getId(), user.getEmail(), true, "Refresh token rotated");
        return new Rotation(newRaw, user);
    }

    /** Revokes one token. Unknown tokens are ignored so the endpoint reveals nothing. */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(TokenUtils.sha256Hex(rawToken)).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.setRevoked(true);
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
            }
            User user = token.getUser();
            auditService.record(AuditAction.LOGOUT, user.getId(), null, true, "User logged out");
        });
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    /** Housekeeping: removes tokens that expired more than a day ago. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        refreshTokenRepository.deleteExpired(Instant.now().minus(Duration.ofDays(1)));
    }
}
