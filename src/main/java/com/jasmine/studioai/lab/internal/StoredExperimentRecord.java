package com.jasmine.studioai.lab.internal;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persisted experiment record (JSON on disk).
 */
@Data
public class StoredExperimentRecord {

    private String recordId;
    private String projectId;
    private String projectName;
    private String owner;
    private String createdAt;

    private String conditions;
    private Map<String, String> conditionsStructured = new LinkedHashMap<>();
    private String dataPath;
    private String conclusion;
    private String failureReason;
    private List<String> tags = new ArrayList<>();
}
