package com.jasmine.studioai.lab.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CompareExperimentsResponse {

    private ExperimentRecordResponse baseline;
    private List<SimilarMatch> similar = new ArrayList<>();

    @Data
    public static class SimilarMatch {
        private ExperimentRecordResponse record;
        /**
         * Jaccard similarity on token sets derived from conditions text (0–1).
         */
        private double conditionsSimilarity;
        private String note;
    }
}
