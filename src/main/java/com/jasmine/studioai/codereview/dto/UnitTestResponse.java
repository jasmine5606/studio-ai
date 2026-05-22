package com.jasmine.studioai.codereview.dto;

import lombok.Data;

@Data
public class UnitTestResponse {
    private String reviewId;
    private String profile;
    private String unitTestCode;
}

