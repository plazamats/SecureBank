package za.co.securebank.config;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import za.co.securebank.entity.Role;
import za.co.securebank.entity.User;
import za.co.securebank.enums.RoleName;
import za.co.securebank.repository.RoleRepository;
import za.co.securebank.repository.UserRepository;

/**
 * Makes sure both roles exist and, only when SECUREBANK_ADMIN_EMAIL and SECUREBANK_ADMIN_PASSWORD are supplied
 * through the environment, creates the first administrator. No default credentials exist in the code base.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapProperties bootstrapProperties;

    @Override
    public void run(ApplicationArguments args) {
        Role userRole = ensureRole(RoleName.ROLE_USER);
        Role adminRole = ensureRole(RoleName.ROLE_ADMIN);

        String email = bootstrapProperties.adminEmail() == null ? "" : bootstrapProperties.adminEmail().trim();
        String password = bootstrapProperties.adminPassword() == null ? "" : bootstrapProperties.adminPassword();
        if (email.isEmpty() || password.isEmpty()) {
            return;
        }
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            return;
        }
        Set<Role> roles = new HashSet<>(Set.of(userRole, adminRole));
        User admin = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(password))
                .firstName("System")
                .lastName("Administrator")
                .emailVerified(true)
                .roles(roles)
                .build();
        userRepository.save(admin);
        log.info("Bootstrap administrator account created");
    }

    private Role ensureRole(RoleName name) {
        return roleRepository.findByName(name)
                .orElseGet(() -> roleRepository.save(Role.builder().name(name).build()));
    }
}
