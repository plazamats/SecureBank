package za.co.securebank.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import za.co.securebank.audit.AuditService;
import za.co.securebank.config.SecurityProperties;
import za.co.securebank.dto.request.ForgotPasswordRequest;
import za.co.securebank.dto.request.LoginRequest;
import za.co.securebank.dto.request.RegisterRequest;
import za.co.securebank.dto.request.ResetPasswordRequest;
import za.co.securebank.dto.request.VerifyEmailRequest;
import za.co.securebank.dto.response.AuthResponse;
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
import za.co.securebank.mapper.UserMapper;
import za.co.securebank.repository.RoleRepository;
import za.co.securebank.repository.UserRepository;
import za.co.securebank.security.JwtService;
import za.co.securebank.security.UserPrincipal;
import za.co.securebank.util.TokenUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String STRONG = "Str0ng!Passw0rd";

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private EmailService emailService;
    @Mock
    private AuditService auditService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final UserMapper userMapper = new UserMapper();

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = buildService(false);
    }

    private AuthService buildService(boolean requireVerification) {
        return new AuthService(authenticationManager, userRepository, roleRepository, passwordEncoder, jwtService,
                refreshTokenService, loginAttemptService, emailService, auditService, userMapper,
                new SecurityProperties(5, 15, requireVerification, 24, 30, 4));
    }

    private User aliceEntity() {
        Role role = Role.builder().id(1L).name(RoleName.ROLE_USER).build();
        return User.builder().id(1L).email("alice@example.com").passwordHash(passwordEncoder.encode(STRONG))
                .firstName("Alice").lastName("Smith").roles(new HashSet<>(Set.of(role))).build();
    }

    private UserPrincipal alicePrincipal(boolean emailVerified) {
        return new UserPrincipal(1L, "alice@example.com", "hash",
                List.of(new SimpleGrantedAuthority("ROLE_USER")), true, null, emailVerified);
    }

    @Test
    void registerHashesThePasswordNormalizesTheEmailAndSendsVerification() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.ROLE_USER))
                .thenReturn(Optional.of(Role.builder().id(1L).name(RoleName.ROLE_USER).build()));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        UserResponse response = service.register(new RegisterRequest("  New@Example.com ", STRONG, " New ", "User", null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("new@example.com", saved.getEmail());
        assertNotEquals(STRONG, saved.getPasswordHash());
        assertTrue(saved.getPasswordHash().startsWith("$2"));
        assertTrue(passwordEncoder.matches(STRONG, saved.getPasswordHash()));
        assertEquals(64, saved.getVerificationTokenHash().length());
        assertEquals("New", saved.getFirstName());
        assertEquals(List.of("ROLE_USER"), response.roles());
        verify(emailService).sendVerificationEmail(any(User.class), anyString());
        verify(auditService).record(eq(AuditAction.USER_REGISTERED), eq(7L), eq("new@example.com"), eq(true), anyString());
    }

    @Test
    void registerRejectsAnEmailThatIsAlreadyTaken() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> service.register(new RegisterRequest("alice@example.com", STRONG, "A", "B", null)));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginWithWrongPasswordRecordsTheFailureAndIssuesNothing() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        when(loginAttemptService.recordFailure("alice@example.com")).thenReturn(true);

        assertThrows(BadCredentialsException.class,
                () -> service.login(new LoginRequest("Alice@Example.com", "wrong")));

        verify(loginAttemptService).recordFailure("alice@example.com");
        verify(refreshTokenService, never()).issue(any());
        verify(jwtService, never()).generateAccessToken(any(UserPrincipal.class));
    }

    @Test
    void loginForAnUnknownAccountIsAuditedWithoutAUserId() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        when(loginAttemptService.recordFailure("ghost@example.com")).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> service.login(new LoginRequest("ghost@example.com", "whatever")));

        verify(auditService).record(eq(AuditAction.LOGIN_FAILURE), eq(null), eq("ghost@example.com"), eq(false), anyString());
    }

    @Test
    void loginOnALockedAccountIsReportedAsLocked() {
        when(authenticationManager.authenticate(any())).thenThrow(new LockedException("locked"));

        assertThrows(AccountLockedException.class,
                () -> service.login(new LoginRequest("alice@example.com", STRONG)));
        verify(loginAttemptService, never()).recordFailure(anyString());
    }

    @Test
    void loginOnASuspendedAccountIsReportedAsSuspended() {
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("disabled"));

        assertThrows(AccountSuspendedException.class,
                () -> service.login(new LoginRequest("alice@example.com", STRONG)));
    }

    @Test
    void successfulLoginReturnsTokensAndResetsFailureCounter() {
        UserPrincipal principal = alicePrincipal(true);
        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(aliceEntity()));
        when(refreshTokenService.issue(1L)).thenReturn("refresh-raw");
        when(jwtService.generateAccessToken(any(UserPrincipal.class))).thenReturn("access-jwt");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

        AuthResponse response = service.login(new LoginRequest("alice@example.com", STRONG));

        assertEquals("access-jwt", response.accessToken());
        assertEquals("refresh-raw", response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900L, response.expiresInSeconds());
        assertEquals("alice@example.com", response.user().email());
        verify(loginAttemptService).recordSuccess(1L);
        verify(auditService).record(eq(AuditAction.LOGIN_SUCCESS), eq(1L), eq("alice@example.com"), eq(true), anyString());
    }

    @Test
    void loginIsBlockedForUnverifiedEmailWhenVerificationIsRequired() {
        AuthService strict = buildService(true);
        UserPrincipal principal = alicePrincipal(false);
        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        assertThrows(EmailNotVerifiedException.class,
                () -> strict.login(new LoginRequest("alice@example.com", STRONG)));
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void verifyEmailMarksTheUserAsVerifiedAndClearsTheToken() {
        User user = aliceEntity();
        user.setVerificationTokenHash(TokenUtils.sha256Hex("good-token"));
        user.setVerificationTokenExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        when(userRepository.findByVerificationTokenHash(TokenUtils.sha256Hex("good-token"))).thenReturn(Optional.of(user));

        service.verifyEmail(new VerifyEmailRequest("good-token"));

        assertTrue(user.isEmailVerified());
        assertNull(user.getVerificationTokenHash());
    }

    @Test
    void verifyEmailRejectsUnknownAndExpiredTokens() {
        when(userRepository.findByVerificationTokenHash(anyString())).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> service.verifyEmail(new VerifyEmailRequest("nope")));

        User user = aliceEntity();
        user.setVerificationTokenExpiresAt(Instant.now().minusSeconds(5));
        when(userRepository.findByVerificationTokenHash(TokenUtils.sha256Hex("old"))).thenReturn(Optional.of(user));
        assertThrows(BadRequestException.class, () -> service.verifyEmail(new VerifyEmailRequest("old")));
        assertEquals(false, user.isEmailVerified());
    }

    @Test
    void forgotPasswordGivesTheSameAnswerForKnownAndUnknownEmails() {
        User user = aliceEntity();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        String known = service.forgotPassword(new ForgotPasswordRequest("alice@example.com")).message();
        String unknown = service.forgotPassword(new ForgotPasswordRequest("ghost@example.com")).message();

        assertEquals(known, unknown);
        verify(emailService).sendPasswordResetEmail(any(User.class), anyString());
        assertEquals(64, user.getResetTokenHash().length());
    }

    @Test
    void resetPasswordChangesThePasswordClearsLockoutAndRevokesSessions() {
        User user = aliceEntity();
        user.setResetTokenHash(TokenUtils.sha256Hex("reset-token"));
        user.setResetTokenExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
        user.setFailedLoginAttempts(3);
        user.setLockedUntil(Instant.now().plusSeconds(600));
        when(userRepository.findByResetTokenHash(TokenUtils.sha256Hex("reset-token"))).thenReturn(Optional.of(user));

        service.resetPassword(new ResetPasswordRequest("reset-token", "N3w!Passw0rdXyz"));

        assertTrue(passwordEncoder.matches("N3w!Passw0rdXyz", user.getPasswordHash()));
        assertNull(user.getResetTokenHash());
        assertNull(user.getLockedUntil());
        assertEquals(0, user.getFailedLoginAttempts());
        verify(refreshTokenService).revokeAllForUser(1L);
    }

    @Test
    void resetPasswordRejectsExpiredTokens() {
        User user = aliceEntity();
        user.setResetTokenExpiresAt(Instant.now().minusSeconds(1));
        when(userRepository.findByResetTokenHash(TokenUtils.sha256Hex("late"))).thenReturn(Optional.of(user));

        assertThrows(BadRequestException.class,
                () -> service.resetPassword(new ResetPasswordRequest("late", "N3w!Passw0rdXyz")));
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }
}
