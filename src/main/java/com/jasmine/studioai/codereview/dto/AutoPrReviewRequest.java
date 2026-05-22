package com.jasmine.studioai.codereview.dto;

import lombok.Data;

@Data
public class AutoPrReviewRequest {
    private String owner;
    private String repo;
    private int prNumber;
    private String oauthToken;
    private String profile = "strict";
    private String context;
    private Integer maxFiles = 60;
    private Integer chunkChars = 3500;
    private Boolean safeMode = true;
}
