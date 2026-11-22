package za.co.securebank.security;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import za.co.securebank.entity.User;
import za.co.securebank.enums.UserStatus;

/**
 * Immutable security view of a user. It is rebuilt from the database on every authenticated request so that
 * suspension and lockout take effect immediately, even for tokens that have not expired yet.
 */
public final class UserPrincipal implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final List<GrantedAuthority> authorities;
    private final boolean enabled;
    private final Instant lockedUntil;
    private final boolean emailVerified;

    public UserPrincipal(Long id, String email, String passwordHash, Collection<? extends GrantedAuthority> authorities,
                         boolean enabled, Instant lockedUntil, boolean emailVerified) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.authorities = List.copyOf(authorities);
        this.enabled = enabled;
        this.lockedUntil = lockedUntil;
        this.emailVerified = emailVerified;
    }

    public static UserPrincipal from(User user) {
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.getName().name()))
                .toList();
        return new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), authorities,
                user.getStatus() == UserStatus.ACTIVE, user.getLockedUntil(), user.isEmailVerified());
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return lockedUntil == null || lockedUntil.isBefore(Instant.now());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
