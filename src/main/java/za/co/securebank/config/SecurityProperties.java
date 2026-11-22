package za.co.securebank.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "securebank.security")
public record SecurityProperties(
        @DefaultValue("5") @Min(1) int maxFailedAttempts,
        @DefaultValue("15") @Min(1) long lockoutMinutes,
        @DefaultValue("false") boolean requireEmailVerification,
        @DefaultValue("24") @Min(1) long verificationTokenHours,
        @DefaultValue("30") @Min(1) long resetTokenMinutes,
        @DefaultValue("12") @Min(4) @Max(18) int bcryptStrength) {
}
