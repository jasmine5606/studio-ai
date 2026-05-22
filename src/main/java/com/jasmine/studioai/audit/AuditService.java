package com.jasmine.studioai.audit;

import com.jasmine.studioai.model.AuditLog;
import com.jasmine.studioai.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void log(String userId, String action, String resource, String resourceId,
                    String details, String ipAddress) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(userId != null ? userId : "anonymous");
            log.setAction(action);
            log.setResource(resource);
            log.setResourceId(resourceId);
            log.setDetails(details);
            log.setIpAddress(ipAddress);
            log.setCreatedAt(Instant.now());
            auditLogRepository.save(log);
        } catch (Exception e) {
            log.warn("Failed to write audit log: {}", e.getMessage());
        }
    }

    public void log(String userId, String action, String resource, String details) {
        log(userId, action, resource, null, details, null);
    }
}
