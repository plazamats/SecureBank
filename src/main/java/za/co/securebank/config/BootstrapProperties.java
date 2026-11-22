package za.co.securebank.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Optional administrator bootstrap. Both values must be provided through the environment for an admin to be created.
 */
@ConfigurationProperties(prefix = "securebank.bootstrap")
public record BootstrapProperties(
        @DefaultValue("") String adminEmail,
        @DefaultValue("") String adminPassword) {
}
