package za.co.securebank.service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.securebank.audit.AuditService;
import za.co.securebank.dto.response.AdminAccountResponse;
import za.co.securebank.dto.response.AdminUserResponse;
import za.co.securebank.dto.response.AuditLogResponse;
import za.co.securebank.entity.AuditLog;
import za.co.securebank.entity.User;
import za.co.securebank.enums.AuditAction;
import za.co.securebank.enums.UserStatus;
import za.co.securebank.exception.BadRequestException;
import za.co.securebank.exception.ResourceNotFoundException;
import za.co.securebank.mapper.AccountMapper;
import za.co.securebank.mapper.AuditLogMapper;
import za.co.securebank.mapper.UserMapper;
import za.co.securebank.repository.AuditLogRepository;
import za.co.securebank.repository.BankAccountRepository;
import za.co.securebank.repository.UserRepository;
import za.co.securebank.security.UserPrincipal;
import za.co.securebank.util.PagingUtils;

/** Administrative use cases. The controller restricts every call to ROLE_ADMIN. */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final BankAccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final UserMapper userMapper;
    private final AccountMapper accountMapper;
    private final AuditLogMapper auditLogMapper;

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(int page, int size) {
        Pageable pageable = PagingUtils.plain(page, size, Sort.by(Sort.Direction.ASC, "id"));
        return userRepository.findAll(pageable).map(userMapper::toAdminResponse);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(Long userId) {
        return userMapper.toAdminResponse(find(userId));
    }

    @Transactional
    public AdminUserResponse suspend(UserPrincipal admin, Long userId) {
        if (admin.getId().equals(userId)) {
            throw new BadRequestException("Administrators cannot suspend their own account");
        }
        User user = find(userId);
        user.setStatus(UserStatus.SUSPENDED);
        user = userRepository.save(user);
        refreshTokenService.revokeAllForUser(userId);
        auditService.record(AuditAction.USER_SUSPENDED, admin.getId(), admin.getEmail(), true, "targetUserId=" + userId);
        return userMapper.toAdminResponse(user);
    }

    @Transactional
    public AdminUserResponse activate(UserPrincipal admin, Long userId) {
        User user = find(userId);
        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user = userRepository.save(user);
        auditService.record(AuditAction.USER_ACTIVATED, admin.getId(), admin.getEmail(), true, "targetUserId=" + userId);
        return userMapper.toAdminResponse(user);
    }

    @Transactional(readOnly = true)
    public Page<AdminAccountResponse> listAccounts(int page, int size) {
        Pageable pageable = PagingUtils.plain(page, size, Sort.by(Sort.Direction.ASC, "id"));
        return accountRepository.findAll(pageable).map(accountMapper::toAdminResponse);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> listAuditLogs(Long userId, AuditAction action, int page, int size) {
        Pageable pageable = PagingUtils.newestFirst(page, size);
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return auditLogRepository.findAll(spec, pageable).map(auditLogMapper::toResponse);
    }

    private User find(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
