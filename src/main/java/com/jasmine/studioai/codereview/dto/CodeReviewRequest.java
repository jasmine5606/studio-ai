package com.jasmine.studioai.codereview.dto;

import lombok.Data;

import java.util.List;

@Data
public class CodeReviewRequest {
    /**
     * Prompt profile name (e.g. "default", "strict").
     */
    private String profile = "default";
    /**
     * If provided, overrides per-file language.
     */
    private String language;
    /**
     * One or more code files to review.
     */
    private List<CodeFile> files;
    /**
     * Optional: extra context from user (e.g. requirements, constraints).
     */
    private String context;
}

