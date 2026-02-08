package com.jasmine.studioai.repository;

import com.jasmine.studioai.model.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    List<AuditLog> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    List<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant start, Instant end, Pageable pageable);
    List<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);
}
