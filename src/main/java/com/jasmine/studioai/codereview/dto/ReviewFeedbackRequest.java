package com.jasmine.studioai.codereview.dto;

import lombok.Data;

@Data
public class ReviewFeedbackRequest {
    /**
     * reviewId returned by API.
     */
    private String reviewId;
    /**
     * prompt profile name used.
     */
    private String profile = "default";
    /**
     * true: false positive; false: helpful/correct.
     */
    private boolean falsePositive;
    /**
     * Optional feedback message.
     */
    private String comment;
}

