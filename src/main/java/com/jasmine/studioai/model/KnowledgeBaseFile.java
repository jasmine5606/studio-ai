package com.jasmine.studioai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "kb_files", indexes = {
        @Index(name = "idx_kb_user", columnList = "userId"),
        @Index(name = "idx_kb_status", columnList = "ingestStatus")
})
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeBaseFile extends BaseEntity {

    @Column(nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 500)
    private String fileName;

    @Column(length = 500)
    private String originalName;

    @Column(length = 100)
    private String fileType;

    private long fileSize;

    @Column(length = 100)
    @Enumerated(EnumType.STRING)
    private IngestStatus ingestStatus = IngestStatus.PENDING;

    private int chunkCount;

    @Column(length = 500)
    private String storagePath;

    @Column(length = 100)
    private String teamId;

    public enum IngestStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}
