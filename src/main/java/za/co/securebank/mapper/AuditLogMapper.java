package za.co.securebank.mapper;

import org.springframework.stereotype.Component;
import za.co.securebank.dto.response.AuditLogResponse;
import za.co.securebank.entity.AuditLog;

@Component
public class AuditLogMapper {

    public AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getUserId(), log.getActorEmail(), log.getAction(),
                log.isSuccess(), log.getDetails(), log.getIpAddress(), log.getCreatedAt());
    }
}
