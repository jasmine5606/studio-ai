package com.jasmine.studioai.codereview.dto;

import lombok.Data;

import java.util.List;

@Data
public class PrReviewResponse {
    private String reviewId;
    private String profile;
    private String owner;
    private String repo;
    private int prNumber;
    private List<FileChangeReview> fileReviews;
    private String summary;

    @Data
    public static class FileChangeReview {
        private String filename;
        private String status;
        private int additions;
        private int deletions;
        private String patch;
        private String review;
    }
}

