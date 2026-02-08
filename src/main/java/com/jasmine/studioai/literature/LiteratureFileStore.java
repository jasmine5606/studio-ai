package com.jasmine.studioai.literature;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jasmine.studioai.literature.internal.LiteratureRegistryFile;
import com.jasmine.studioai.literature.internal.StoredLiteratureItem;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@RequiredArgsConstructor
public class LiteratureFileStore {

    private final ObjectMapper objectMapper;

    @Value("${lab.literature.storage-dir:./lab-data/literature}")
    private String baseDir;

    public Path userRoot(String owner) {
        return Paths.get(baseDir, safeSegment(owner));
    }

    public Path registryPath(String owner) {
        return userRoot(owner).resolve("registry.json");
    }

    public Path docDir(String owner, String literatureId) {
        return userRoot(owner).resolve("docs").resolve(safeSegment(literatureId));
    }

    public LiteratureRegistryFile loadRegistry(String owner) throws IOException {
        Path p = registryPath(owner);
        if (!Files.isRegularFile(p)) {
            return new LiteratureRegistryFile();
        }
        return objectMapper.readValue(p.toFile(), LiteratureRegistryFile.class);
    }

    public void saveRegistry(String owner, LiteratureRegistryFile reg) throws IOException {
        Path root = userRoot(owner);
        Files.createDirectories(root);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(registryPath(owner).toFile(), reg);
    }

    public void writeExtractedText(String owner, String literatureId, String text) throws IOException {
        Path dir = docDir(owner, literatureId);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("extracted.txt"), text == null ? "" : text, StandardCharsets.UTF_8);
    }

    public String readExtractedText(String owner, String literatureId) throws IOException {
        Path f = docDir(owner, literatureId).resolve("extracted.txt");
        if (!Files.isRegularFile(f)) return "";
        return Files.readString(f, StandardCharsets.UTF_8);
    }

    public void writeUploadedBinary(String owner, String literatureId, String filename, byte[] bytes) throws IOException {
        Path dir = docDir(owner, literatureId);
        Files.createDirectories(dir);
        String name = filename == null || filename.isBlank() ? "upload.bin" : safeFilename(filename);
        Files.write(dir.resolve(name), bytes);
    }

    public void deleteDocTree(String owner, String literatureId) throws IOException {
        Path dir = docDir(owner, literatureId);
        if (!Files.isDirectory(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }

    public void upsertItem(String owner, StoredLiteratureItem item) throws IOException {
        LiteratureRegistryFile reg = loadRegistry(owner);
        reg.getItems().removeIf(i -> item.getLiteratureId().equals(i.getLiteratureId()));
        reg.getItems().add(item);
        saveRegistry(owner, reg);
    }

    public void removeItem(String owner, String literatureId) throws IOException {
        LiteratureRegistryFile reg = loadRegistry(owner);
        reg.getItems().removeIf(i -> literatureId.equals(i.getLiteratureId()));
        saveRegistry(owner, reg);
    }

    public StoredLiteratureItem findItem(String owner, String literatureId) throws IOException {
        for (StoredLiteratureItem i : loadRegistry(owner).getItems()) {
            if (literatureId.equals(i.getLiteratureId())) return i;
        }
        return null;
    }

    private static String safeSegment(String raw) {
        if (raw == null || raw.isBlank()) return "_";
        String s = raw.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return s.isBlank() ? "_" : s;
    }

    private static String safeFilename(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
