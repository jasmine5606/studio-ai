package com.jasmine.studioai.kb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jasmine.studioai.kb.dto.KbDocInfo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class KnowledgeBaseRegistry {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${kb.storage-dir:./knowledge-base/uploads}")
    private String storageDir;

    private static final String DOCS_SET = "kb:docs";
    private static final Duration META_TTL = Duration.ofDays(365);
    private static final String SNAPSHOT_FILE = "registry-snapshot.json";

    private final Map<String, KbMetadata> localDocs = new ConcurrentHashMap<>();
    private final Map<String, List<String>> localDocChunks = new ConcurrentHashMap<>();
    private final Map<String, String> localChunkText = new ConcurrentHashMap<>();
    private final Map<String, String> localChunkVectorId = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadSnapshot();
        syncSnapshotToRedis();
    }

    public Path saveUploadedFile(String docId, String originalName, byte[] bytes) throws IOException {
        Path dir = Paths.get(storageDir, docId);
        Files.createDirectories(dir);
        Path target = dir.resolve(originalName);
        Files.write(target, bytes);
        return target;
    }

    public void deleteUploadedFile(String docId) {
        Path dir = Paths.get(storageDir, docId);
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    public void registerDoc(KbMetadata meta) {
        localDocs.put(meta.docId(), meta);
        persistSnapshot();
        try {
            redis.opsForHash().putAll(docKey(meta.docId()), meta.toMap());
            redis.opsForSet().add(DOCS_SET, meta.docId());
            redis.expire(docKey(meta.docId()), META_TTL);
        } catch (Exception ignored) {
        }
    }

    public void deleteDoc(String docId) {
        localDocs.remove(docId);
        localDocChunks.remove(docId);
        persistSnapshot();
        try {
            redis.delete(docKey(docId));
            redis.opsForSet().remove(DOCS_SET, docId);
            redis.delete(docChunksKey(docId));
        } catch (Exception ignored) {
        }
    }

    public List<KbDocInfo> listDocs() {
        try {
            Set<String> ids = redis.opsForSet().members(DOCS_SET);
            if (ids != null && !ids.isEmpty()) {
                return ids.stream().sorted().map(this::getDocInfo).filter(Objects::nonNull).collect(Collectors.toList());
            }
        } catch (Exception ignored) {
        }
        return localDocs.values().stream()
                .map(this::toDocInfo)
                .sorted((a, b) -> a.getDocId().compareToIgnoreCase(b.getDocId()))
                .collect(Collectors.toList());
    }

    public KbDocInfo getDocInfo(String docId) {
        try {
            Map<Object, Object> map = redis.opsForHash().entries(docKey(docId));
            if (map != null && !map.isEmpty()) {
                KbDocInfo info = new KbDocInfo();
                info.setDocId(docId);
                info.setFilename(str(map.get("filename")));
                info.setContentType(str(map.get("contentType")));
                info.setSource(str(map.get("source")));
                info.setTags(str(map.get("tags")));
                info.setCreatedAt(str(map.get("createdAt")));
                info.setChunks(getDocChunks(docId).size());
                return info;
            }
        } catch (Exception ignored) {
        }
        KbMetadata meta = localDocs.get(docId);
        return meta == null ? null : toDocInfo(meta);
    }

    public void linkDocChunks(String docId, List<String> chunkIds) {
        localDocChunks.put(docId, new ArrayList<>(chunkIds == null ? List.of() : chunkIds));
        persistSnapshot();
        try {
            redis.opsForValue().set(docChunksKey(docId), objectMapper.writeValueAsString(chunkIds), META_TTL);
        } catch (Exception ignored) {
        }
    }

    public List<String> getDocChunks(String docId) {
        try {
            String json = redis.opsForValue().get(docChunksKey(docId));
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, new TypeReference<>() {});
            }
        } catch (Exception ignored) {
        }
        return localDocChunks.getOrDefault(docId, List.of());
    }

    public void saveChunkText(String chunkId, String text) {
        localChunkText.put(chunkId, text == null ? "" : text);
        persistSnapshot();
        try {
            redis.opsForValue().set(chunkTextKey(chunkId), text, META_TTL);
        } catch (Exception ignored) {
        }
    }

    public String getChunkText(String chunkId) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(chunkTextKey(chunkId))).orElse("");
        } catch (Exception ignored) {
        }
        return localChunkText.getOrDefault(chunkId, "");
    }

    public void deleteChunkText(String chunkId) {
        localChunkText.remove(chunkId);
        persistSnapshot();
        try {
            redis.delete(chunkTextKey(chunkId));
        } catch (Exception ignored) {
        }
    }

    public void saveChunkVectorId(String chunkId, String vectorId) {
        if (vectorId == null || vectorId.isBlank()) return;
        localChunkVectorId.put(chunkId, vectorId);
        persistSnapshot();
        try {
            redis.opsForValue().set(chunkVectorKey(chunkId), vectorId, META_TTL);
        } catch (Exception ignored) {
        }
    }

    public String getChunkVectorId(String chunkId) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(chunkVectorKey(chunkId))).orElse("");
        } catch (Exception ignored) {
        }
        return localChunkVectorId.getOrDefault(chunkId, "");
    }

    public void deleteChunkVectorId(String chunkId) {
        localChunkVectorId.remove(chunkId);
        persistSnapshot();
        try {
            redis.delete(chunkVectorKey(chunkId));
        } catch (Exception ignored) {
        }
    }

    private KbDocInfo toDocInfo(KbMetadata meta) {
        KbDocInfo info = new KbDocInfo();
        info.setDocId(meta.docId());
        info.setFilename(meta.filename());
        info.setContentType(meta.contentType());
        info.setSource(meta.source());
        info.setTags(meta.tags());
        info.setCreatedAt(meta.createdAt());
        info.setChunks(getDocChunks(meta.docId()).size());
        return info;
    }

    private void syncSnapshotToRedis() {
        if (localDocs.isEmpty()) return;
        try {
            for (KbMetadata meta : localDocs.values()) {
                redis.opsForHash().putAll(docKey(meta.docId()), meta.toMap());
                redis.opsForSet().add(DOCS_SET, meta.docId());
            }
            for (Map.Entry<String, List<String>> e : localDocChunks.entrySet()) {
                redis.opsForValue().set(docChunksKey(e.getKey()), objectMapper.writeValueAsString(e.getValue()), META_TTL);
            }
            for (Map.Entry<String, String> e : localChunkText.entrySet()) {
                redis.opsForValue().set(chunkTextKey(e.getKey()), e.getValue(), META_TTL);
            }
            for (Map.Entry<String, String> e : localChunkVectorId.entrySet()) {
                redis.opsForValue().set(chunkVectorKey(e.getKey()), e.getValue(), META_TTL);
            }
        } catch (Exception ignored) {
        }
    }

    private void loadSnapshot() {
        Path snapshot = snapshotPath();
        if (!Files.exists(snapshot)) return;
        try {
            SnapshotData data = objectMapper.readValue(snapshot.toFile(), SnapshotData.class);
            if (data.docs != null) localDocs.putAll(data.docs);
            if (data.docChunks != null) localDocChunks.putAll(data.docChunks);
            if (data.chunkText != null) localChunkText.putAll(data.chunkText);
            if (data.chunkVectorId != null) localChunkVectorId.putAll(data.chunkVectorId);
        } catch (Exception ignored) {
        }
    }

    private void persistSnapshot() {
        try {
            Files.createDirectories(Paths.get(storageDir));
            SnapshotData data = new SnapshotData();
            data.docs = new HashMap<>(localDocs);
            data.docChunks = new HashMap<>(localDocChunks);
            data.chunkText = new HashMap<>(localChunkText);
            data.chunkVectorId = new HashMap<>(localChunkVectorId);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(snapshotPath().toFile(), data);
        } catch (Exception ignored) {
        }
    }

    private Path snapshotPath() {
        return Paths.get(storageDir, SNAPSHOT_FILE);
    }

    private static String docKey(String docId) {
        return "kb:doc:" + docId;
    }

    private static String docChunksKey(String docId) {
        return "kb:doc:" + docId + ":chunks";
    }

    private static String chunkTextKey(String chunkId) {
        return "kb:chunk:" + chunkId + ":text";
    }

    private static String chunkVectorKey(String chunkId) {
        return "kb:chunk:" + chunkId + ":vectorId";
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static class SnapshotData {
        public Map<String, KbMetadata> docs;
        public Map<String, List<String>> docChunks;
        public Map<String, String> chunkText;
        public Map<String, String> chunkVectorId;
    }
}
