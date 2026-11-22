package za.co.securebank.dto.response;

import java.time.Instant;
import za.co.securebank.enums.AuditAction;

public record AuditLogResponse(
        Long id,
        Long userId,
        String actorEmail,
        AuditAction action,
        boolean success,
        String details,
        String ipAddress,
        Instant createdAt) {
}
