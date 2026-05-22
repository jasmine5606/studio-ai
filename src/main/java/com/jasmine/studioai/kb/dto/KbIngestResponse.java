package com.jasmine.studioai.kb.dto;

import lombok.Data;

@Data
public class KbIngestResponse {
    private String docId;
    private String filename;
    private int chunks;
    private String storedPath;
}

