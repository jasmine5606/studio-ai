package com.jasmine.studioai.lab.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class CreateExperimentRequest {

    /**
     * Logical project / topic id (e.g. lab topic code).
     */
    private String projectId;

    /**
     * Optional display name for dashboard.
     */
    private String projectName;

    /**
     * Free-text experimental conditions (for search & similarity).
     */
    private String conditions;

    /**
     * Optional structured fields (instrument, temperature, batch id, ...).
     */
    private Map<String, String> conditionsStructured = new LinkedHashMap<>();

    private String dataPath;
    private String conclusion;
    private String failureReason;

    private List<String> tags = new ArrayList<>();
}
