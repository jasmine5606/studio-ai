package com.jasmine.studioai.codereview.dto;

import lombok.Data;

import java.util.List;

@Data
public class CodeReviewResponse {
    private String reviewId;
    private String profile;
    private List<FileReview> fileReviews;

    @Data
    public static class FileReview {
        private String path;
        private String language;
        private String review;
    }
}

