package com.jasmine.studioai.kb;

import dev.langchain4j.data.document.Metadata;

import java.util.HashMap;
import java.util.Map;

public record KbMetadata(String docId,
                         String filename,
                         String contentType,
                         String source,
                         String tags,
                         String createdAt) {

    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        map.put("docId", docId);
        map.put("filename", filename);
        map.put("contentType", contentType);
        map.put("source", source);
        map.put("tags", tags);
        map.put("createdAt", createdAt);
        return map;
    }

    public Metadata toMetadata() {
        Metadata m = new Metadata();
        m.put("docId", docId);
        m.put("filename", filename);
        m.put("contentType", contentType);
        if (source != null && !source.isBlank()) m.put("source", source);
        if (tags != null && !tags.isBlank()) m.put("tags", tags);
        if (createdAt != null && !createdAt.isBlank()) m.put("createdAt", createdAt);
        return m;
    }
}

