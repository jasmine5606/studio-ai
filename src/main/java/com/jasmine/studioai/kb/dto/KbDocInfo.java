package com.jasmine.studioai.kb.dto;

import lombok.Data;

@Data
public class KbDocInfo {
    private String docId;
    private String filename;
    private String contentType;
    private String source;
    private String tags;
    private String createdAt;
    private int chunks;
}

