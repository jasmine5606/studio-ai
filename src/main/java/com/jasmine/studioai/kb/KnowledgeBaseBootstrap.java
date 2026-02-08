package com.jasmine.studioai.kb;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeBaseBootstrap implements ApplicationRunner {

    private final KnowledgeBaseMaintenanceService maintenanceService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            var result = maintenanceService.reindexBm25();
            log.info("KB bootstrap reindex done: docs={}, chunks={}", result.docs(), result.chunks());
        } catch (Exception e) {
            log.warn("KB bootstrap reindex skipped: {}", e.getMessage());
        }
    }
}
