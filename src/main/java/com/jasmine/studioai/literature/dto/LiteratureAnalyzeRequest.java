package com.jasmine.studioai.literature.dto;

import lombok.Data;

@Data
public class LiteratureAnalyzeRequest {

    private String literatureId;

    /**
     * Your lab topic / goal text to tailor questions and related-work angle.
     */
    private String projectContext;

    private String profile = "default";
}
