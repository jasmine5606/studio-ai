package com.jasmine.studioai.lab.internal;

import lombok.Data;

/**
 * Optional display metadata for a project directory.
 */
@Data
public class StoredProjectMeta {

    private String projectId;
    private String projectName;
    private String owner;
    private String updatedAt;
}
