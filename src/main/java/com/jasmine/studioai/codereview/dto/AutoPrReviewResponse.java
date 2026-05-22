package com.jasmine.studioai.codereview.dto;

import lombok.Data;

import java.util.List;

@Data
public class AutoPrReviewResponse {
    private String reviewId;
    private String owner;
    private String repo;
    private int prNumber;
    private boolean mcpEnabled;
    private int analyzedFiles;
    private int analyzedChunks;
    private String summary;
    private List<FileResult> files;

    @Data
    public static class FileResult {
        private String filename;
        private String status;
        private int chunkCount;
        private String review;
    }
}
