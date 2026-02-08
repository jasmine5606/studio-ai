package com.jasmine.studioai.literature.internal;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StoredLiteratureItem {

    private String literatureId;
    private String owner;
    private String title;
    /**
     * pdf | arxiv | doi
     */
    private String source;
    private String identifier;
    private String projectTag;
    private String createdAt;

    /**
     * Relative path under user storage dir to full extracted plain text.
     */
    private String extractedTextRelativePath;

    private List<String> vectorIds = new ArrayList<>();
}
