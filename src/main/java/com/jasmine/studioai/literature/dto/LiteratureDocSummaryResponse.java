package com.jasmine.studioai.literature.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LiteratureDocSummaryResponse {

    private String literatureId;
    private String title;
    private String source;
    private String identifier;
    private String projectTag;
    private String createdAt;
    private int chunkCount;
    private String textPreview;
    private List<String> chunkCitationIds = new ArrayList<>();
}
