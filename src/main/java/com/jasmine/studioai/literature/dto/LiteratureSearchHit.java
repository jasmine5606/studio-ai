package com.jasmine.studioai.literature.dto;

import lombok.Data;

@Data
public class LiteratureSearchHit {

    private String citationId;
    private String literatureId;
    private int chunkIndex;
    private double score;
    private String text;
    private String title;
}
