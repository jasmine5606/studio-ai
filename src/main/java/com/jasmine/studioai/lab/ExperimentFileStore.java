package com.jasmine.studioai.lab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jasmine.studioai.lab.internal.StoredExperimentRecord;
import com.jasmine.studioai.lab.internal.StoredProjectMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * File-backed storage: {@code {baseDir}/{owner}/projects/{projectId}/*.json}.
 */
@Component
@RequiredArgsConstructor
public class ExperimentFileStore {

    private final ObjectMapper objectMapper;

    @Value("${lab.experiments.storage-dir:./lab-data/experiments}")
    private String baseDir;

    public Path projectDir(String owner, String projectId) {
        return Paths.get(baseDir, safeSegment(owner), "projects", safeSegment(projectId));
    }

    public Path recordPath(String owner, String projectId, String recordId) {
        return projectDir(owner, projectId).resolve(safeSegment(recordId) + ".json");
    }

    public Path projectMetaPath(String owner, String projectId) {
        return projectDir(owner, projectId).resolve("_project.json");
    }

    public void saveRecord(StoredExperimentRecord record) throws IOException {
        Path dir = projectDir(record.getOwner(), record.getProjectId());
        Files.createDirectories(dir);
        Path file = recordPath(record.getOwner(), record.getProjectId(), record.getRecordId());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), record);
    }

    public Optional<StoredExperimentRecord> loadRecord(String owner, String projectId, String recordId) throws IOException {
        Path file = recordPath(owner, projectId, recordId);
        if (!Files.isRegularFile(file)) return Optional.empty();
        return Optional.of(objectMapper.readValue(file.toFile(), StoredExperimentRecord.class));
    }

    public List<StoredExperimentRecord> listProjectRecords(String owner, String projectId) throws IOException {
        Path dir = projectDir(owner, projectId);
        if (!Files.isDirectory(dir)) return List.of();
        List<StoredExperimentRecord> out = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : stream.toList()) {
                if (!Files.isRegularFile(p)) continue;
                String name = p.getFileName().toString();
                if (!name.endsWith(".json") || "_project.json".equals(name)) continue;
                out.add(objectMapper.readValue(p.toFile(), StoredExperimentRecord.class));
            }
        }
        out.sort(Comparator.comparing(StoredExperimentRecord::getCreatedAt).reversed());
        return out;
    }

    public List<String> listProjectIds(String owner) throws IOException {
        Path root = Paths.get(baseDir, safeSegment(owner), "projects");
        if (!Files.isDirectory(root)) return List.of();
        List<String> ids = new ArrayList<>();
        try (Stream<Path> stream = Files.list(root)) {
            for (Path p : stream.toList()) {
                if (Files.isDirectory(p)) ids.add(p.getFileName().toString());
            }
        }
        ids.sort(String::compareToIgnoreCase);
        return ids;
    }

    public void upsertProjectMeta(String owner, String projectId, String projectName) throws IOException {
        Path dir = projectDir(owner, projectId);
        Files.createDirectories(dir);
        StoredProjectMeta meta = new StoredProjectMeta();
        meta.setProjectId(projectId);
        meta.setProjectName(projectName == null ? "" : projectName.trim());
        meta.setOwner(owner);
        meta.setUpdatedAt(Instant.now().toString());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(projectMetaPath(owner, projectId).toFile(), meta);
    }

    public Optional<StoredProjectMeta> loadProjectMeta(String owner, String projectId) throws IOException {
        Path f = projectMetaPath(owner, projectId);
        if (!Files.isRegularFile(f)) return Optional.empty();
        return Optional.of(objectMapper.readValue(f.toFile(), StoredProjectMeta.class));
    }

    private static String safeSegment(String raw) {
        if (raw == null || raw.isBlank()) return "_";
        String s = raw.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return s.isBlank() ? "_" : s;
    }
}
