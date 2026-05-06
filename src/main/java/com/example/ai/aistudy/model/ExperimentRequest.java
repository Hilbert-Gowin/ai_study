package com.example.ai.aistudy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentRequest {
    private List<String> questions;
    private List<ExperimentConfig> experiments;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentConfig {
        private String name;
        private int topK;
        private int chunkSize;
        private int maxTokens;
    }
}
