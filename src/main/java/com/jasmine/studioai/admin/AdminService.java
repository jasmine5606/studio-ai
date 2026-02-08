package com.jasmine.studioai.admin;

import com.jasmine.studioai.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final KnowledgeBaseFileRepository kbFileRepository;
    private final LiteratureDocumentRepository literatureRepository;
    private final ExperimentProjectRepository experimentProjectRepository;
    private final AuditLogRepository auditLogRepository;

    public Map<String, Object> dashboard() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("totalSessions", chatSessionRepository.count());
        stats.put("totalMessages", chatMessageRepository.count());
        stats.put("totalKbFiles", kbFileRepository.count());
        stats.put("totalLiteratureDocs", literatureRepository.count());
        stats.put("totalExperimentProjects", experimentProjectRepository.count());
        stats.put("timestamp", Instant.now().toString());
        return stats;
    }

    public Map<String, Object> usageStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("activeSessions", chatSessionRepository.count());
        stats.put("totalMessages", chatMessageRepository.count());
        stats.put("knowledgeBaseFiles", kbFileRepository.count());
        stats.put("literatureDocuments", literatureRepository.count());
        stats.put("experimentRecords", experimentProjectRepository.count());
        return stats;
    }
}
