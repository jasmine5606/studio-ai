package com.jasmine.studioai.literature.dto;

import lombok.Data;

@Data
public class LiteratureIdentifierRequest {

    /**
     * arXiv id (e.g. 2301.12345), DOI (e.g. 10.1145/123), or URL containing them.
     */
    private String identifier;

    private String projectTag;
}
