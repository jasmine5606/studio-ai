package com.jasmine.studioai.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jasmine.studioai.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private final ObjectMapper objectMapper;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final KnowledgeBaseFileRepository kbFileRepository;
    private final LiteratureDocumentRepository literatureRepository;
    private final ExperimentProjectRepository experimentProjectRepository;

    private static final Path BACKUP_DIR = Paths.get("./backups");

    /**
     * Scheduled backup every day at 3:00 AM
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledBackup() {
        try {
            String filename = "backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json";
            Path backupFile = BACKUP_DIR.resolve(filename);
            Files.createDirectories(BACKUP_DIR);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("backupTime", Instant.now().toString());
            data.put("sessions", chatSessionRepository.findAll());
            data.put("messages", chatMessageRepository.findAll());
            data.put("kbFiles", kbFileRepository.findAll());
            data.put("literature", literatureRepository.findAll());

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(backupFile.toFile(), data);
            log.info("Backup completed: {}", filename);

            cleanupOldBackups(30);
        } catch (IOException e) {
            log.error("Backup failed: {}", e.getMessage(), e);
        }
    }

    public String manualBackup() throws IOException {
        String filename = "backup-manual-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json";
        Path backupFile = BACKUP_DIR.resolve(filename);
        Files.createDirectories(BACKUP_DIR);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("backupTime", Instant.now().toString());
        data.put("sessions", chatSessionRepository.findAll());
        data.put("messages", chatMessageRepository.findAll());
        data.put("kbFiles", kbFileRepository.findAll());
        data.put("literature", literatureRepository.findAll());
        data.put("experiments", experimentProjectRepository.findAll());

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(backupFile.toFile(), data);
        log.info("Manual backup completed: {}", filename);
        return filename;
    }

    private void cleanupOldBackups(int keepDays) {
        try {
            Instant cutoff = Instant.now().minusSeconds(keepDays * 86400L);
            Files.list(BACKUP_DIR)
                    .filter(Files::isRegularFile)
                    .filter(f -> {
                        try {
                            return Files.getLastModifiedTime(f).toInstant().isBefore(cutoff);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(f -> {
                        try {
                            Files.deleteIfExists(f);
                        } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }
}
