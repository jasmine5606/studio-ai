package com.jasmine.studioai.codereview.dto;

import lombok.Data;

@Data
public class UnitTestRequest {
    private String profile = "default";
    private String language;
    private String code;
    /**
     * Optional: target test framework hint (e.g. junit5, testng).
     */
    private String framework = "junit5";
}

