package com.jasmine.studioai.kb.dto;

import lombok.Data;

import java.util.List;

@Data
public class KbSearchResponse {
    private String query;
    private String mode;
    private List<Item> items;

    @Data
    public static class Item {
        private String chunkId;
        private String docId;
        private double score;
        private String text;
    }
}

