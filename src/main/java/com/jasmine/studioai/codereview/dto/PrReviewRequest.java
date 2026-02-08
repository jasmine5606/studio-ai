package com.jasmine.studioai.codereview.dto;

import lombok.Data;

@Data
public class PrReviewRequest {
    private String profile = "default";
    private String owner;
    private String repo;
    private int prNumber;
    /**
     * Optional: branch/ref for file fetch (falls back to PR head sha).
     */
    private String ref;
    /**
     * Optional: extra context from user (e.g. PR intent).
     */
    private String context;
}

