package it.fn.redfish.catalog.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import it.fn.redfish.catalog.domain.AuditLog;
import it.fn.redfish.catalog.repo.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, Object entityId, String detail) {
        AuditLog log = new AuditLog(currentUsername(), action);
        log.setEntityType(entityType);
        log.setEntityId(entityId == null ? null : String.valueOf(entityId));
        log.setDetail(detail);
        log.setIpAddress(currentIp());
        repository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String action, String entityType, Object entityId, String detail) {
        AuditLog log = new AuditLog(currentUsername(), action);
        log.setEntityType(entityType);
        log.setEntityId(entityId == null ? null : String.valueOf(entityId));
        log.setDetail(detail);
        log.setIpAddress(currentIp());
        log.setOutcome("KO");
        repository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> page(int page, int size) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(page, 0), size));
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "system";
        }
        return auth.getName();
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }
}
