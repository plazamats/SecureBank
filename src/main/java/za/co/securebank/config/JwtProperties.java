package za.co.securebank.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * JWT settings. The secret is a Base64 encoded key of at least 32 bytes and is always read from the environment.
 */
@Validated
@ConfigurationProperties(prefix = "securebank.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @DefaultValue("15") @Min(1) long accessTokenMinutes,
        @DefaultValue("7") @Min(1) long refreshTokenDays,
        @DefaultValue("securebank-api") @NotBlank String issuer) {
}
