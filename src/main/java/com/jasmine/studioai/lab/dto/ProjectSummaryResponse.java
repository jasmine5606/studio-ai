package com.jasmine.studioai.lab.dto;

import lombok.Data;

@Data
public class ProjectSummaryResponse {

    private String projectId;
    private String projectName;
    private int experimentCount;
    private String lastExperimentAt;
}
