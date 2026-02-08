package com.jasmine.studioai.codereview.dto;

import lombok.Data;

@Data
public class CodeFile {
    /**
     * Optional: file path (e.g. src/main/java/...).
     */
    private String path;
    /**
     * File content.
     */
    private String content;
    /**
     * Optional: language hint (e.g. java, js, py).
     */
    private String language;
}

