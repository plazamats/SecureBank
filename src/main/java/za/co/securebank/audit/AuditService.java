package za.co.securebank.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import za.co.securebank.entity.AuditLog;
import za.co.securebank.enums.AuditAction;
import za.co.securebank.repository.AuditLogRepository;
import za.co.securebank.security.SecurityUtils;

/**
 * Writes audit records. It joins the caller's transaction when there is one, so the audit entry of a successful
 * operation commits atomically with the operation itself. Details must never contain passwords, tokens or amounts.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void record(AuditAction action, Long userId, String actorEmail, boolean success, String details) {
        AuditLog entry = AuditLog.builder()
                .action(action)
                .userId(userId)
                .actorEmail(truncate(actorEmail, 255))
                .success(success)
                .details(truncate(details, 500))
                .ipAddress(truncate(currentIpAddress(), 45))
                .build();
        auditLogRepository.save(entry);
    }

    /** Records an event for whoever is authenticated in the current security context (anonymous if nobody). */
    @Transactional
    public void recordForCurrentUser(AuditAction action, boolean success, String details) {
        SecurityUtils.currentUser().ifPresentOrElse(
                user -> record(action, user.getId(), user.getEmail(), success, details),
                () -> record(action, null, null, success, details));
    }

    private static String currentIpAddress() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
