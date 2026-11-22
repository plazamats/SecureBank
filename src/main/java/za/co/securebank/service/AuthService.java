package za.co.securebank.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.securebank.audit.AuditService;
import za.co.securebank.config.SecurityProperties;
import za.co.securebank.dto.request.ForgotPasswordRequest;
import za.co.securebank.dto.request.LoginRequest;
import za.co.securebank.dto.request.RefreshTokenRequest;
import za.co.securebank.dto.request.RegisterRequest;
import za.co.securebank.dto.request.ResetPasswordRequest;
import za.co.securebank.dto.request.VerifyEmailRequest;
import za.co.securebank.dto.response.AuthResponse;
import za.co.securebank.dto.response.MessageResponse;
import za.co.securebank.dto.response.UserResponse;
import za.co.securebank.entity.Role;
import za.co.securebank.entity.User;
import za.co.securebank.enums.AuditAction;
import za.co.securebank.enums.RoleName;
import za.co.securebank.exception.AccountLockedException;
import za.co.securebank.exception.AccountSuspendedException;
import za.co.securebank.exception.BadRequestException;
import za.co.securebank.exception.DuplicateResourceException;
import za.co.securebank.exception.EmailNotVerifiedException;
import za.co.securebank.exception.InvalidRefreshTokenException;
import za.co.securebank.mapper.UserMapper;
import za.co.securebank.repository.RoleRepository;
import za.co.securebank.repository.UserRepository;
import za.co.securebank.security.JwtService;
import za.co.securebank.security.UserPrincipal;
import za.co.securebank.util.TokenUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final String GENERIC_RESET_MESSAGE =
            "If an account exists for that email, password reset instructions have been sent";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final UserMapper userMapper;
    private final SecurityProperties securityProperties;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("An account with this email address already exists");
        }
        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseThrow(() -> new IllegalStateException("Role ROLE_USER is missing from the database"));

        String rawVerificationToken = TokenUtils.generateToken();
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .phone(blankToNull(request.phone()))
                .roles(new HashSet<>(Set.of(userRole)))
                .verificationTokenHash(TokenUtils.sha256Hex(rawVerificationToken))
                .verificationTokenExpiresAt(Instant.now().plus(Duration.ofHours(securityProperties.verificationTokenHours())))
                .build();
        user = userRepository.save(user);

        auditService.record(AuditAction.USER_REGISTERED, user.getId(), user.getEmail(), true, "New user registered");
        emailService.sendVerificationEmail(user, rawVerificationToken);
        return userMapper.toResponse(user);
    }

    /**
     * Not transactional on purpose: failed login bookkeeping must be committed even though an exception is thrown.
     */
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (BadCredentialsException ex) {
            boolean knownAccount = loginAttemptService.recordFailure(email);
            if (!knownAccount) {
                auditService.record(AuditAction.LOGIN_FAILURE, null, email, false, "Login attempt for unknown account");
            }
            throw ex;
        } catch (LockedException ex) {
            auditService.record(AuditAction.LOGIN_FAILURE, null, email, false, "Login attempt on a locked account");
            throw new AccountLockedException();
        } catch (DisabledException ex) {
            auditService.record(AuditAction.LOGIN_FAILURE, null, email, false, "Login attempt on a suspended account");
            throw new AccountSuspendedException();
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        if (securityProperties.requireEmailVerification() && !principal.isEmailVerified()) {
            throw new EmailNotVerifiedException("Please verify your email address before logging in");
        }

        loginAttemptService.recordSuccess(principal.getId());
        auditService.record(AuditAction.LOGIN_SUCCESS, principal.getId(), principal.getEmail(), true, "User logged in");

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return buildAuthResponse(principal, refreshTokenService.issue(principal.getId()), user);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(request.refreshToken());
        User user = rotation.user();
        return buildAuthResponse(UserPrincipal.from(user), rotation.refreshToken(), user);
    }

    public MessageResponse logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
        return new MessageResponse("Logged out successfully");
    }

    @Transactional
    public MessageResponse verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByVerificationTokenHash(TokenUtils.sha256Hex(request.token()))
                .orElseThrow(() -> new BadRequestException("The verification token is invalid or has expired"));
        if (user.getVerificationTokenExpiresAt() == null || user.getVerificationTokenExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("The verification token is invalid or has expired");
        }
        user.setEmailVerified(true);
        user.setVerificationTokenHash(null);
        user.setVerificationTokenExpiresAt(null);
        userRepository.save(user);
        auditService.record(AuditAction.EMAIL_VERIFIED, user.getId(), user.getEmail(), true, "Email address verified");
        return new MessageResponse("Email address verified");
    }

    /** Always returns the same message so the endpoint cannot be used to discover which emails are registered. */
    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(normalizeEmail(request.email())).ifPresent(user -> {
            String rawToken = TokenUtils.generateToken();
            user.setResetTokenHash(TokenUtils.sha256Hex(rawToken));
            user.setResetTokenExpiresAt(Instant.now().plus(Duration.ofMinutes(securityProperties.resetTokenMinutes())));
            userRepository.save(user);
            auditService.record(AuditAction.PASSWORD_RESET_REQUESTED, user.getId(), user.getEmail(), true,
                    "Password reset requested");
            emailService.sendPasswordResetEmail(user, rawToken);
        });
        return new MessageResponse(GENERIC_RESET_MESSAGE);
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByResetTokenHash(TokenUtils.sha256Hex(request.token()))
                .orElseThrow(() -> new BadRequestException("The reset token is invalid or has expired"));
        if (user.getResetTokenExpiresAt() == null || user.getResetTokenExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("The reset token is invalid or has expired");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setResetTokenHash(null);
        user.setResetTokenExpiresAt(null);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
        auditService.record(AuditAction.PASSWORD_RESET_COMPLETED, user.getId(), user.getEmail(), true,
                "Password reset completed, all sessions revoked");
        return new MessageResponse("Password has been reset. Please log in with your new password");
    }

    private AuthResponse buildAuthResponse(UserPrincipal principal, String refreshToken, User user) {
        return new AuthResponse(jwtService.generateAccessToken(principal), refreshToken, TOKEN_TYPE,
                jwtService.getAccessTokenTtlSeconds(), userMapper.toResponse(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
