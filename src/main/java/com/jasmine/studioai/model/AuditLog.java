package com.jasmine.studioai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_user", columnList = "userId"),
        @Index(name = "idx_audit_time", columnList = "createdAt"),
        @Index(name = "idx_audit_action", columnList = "action")
})
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36, updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(length = 500)
    private String resource;

    @Column(length = 200)
    private String resourceId;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(length = 100)
    private String ipAddress;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
