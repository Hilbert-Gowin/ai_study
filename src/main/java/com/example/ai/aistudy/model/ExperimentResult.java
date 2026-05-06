package com.example.ai.aistudy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentResult {
    private String experimentName;
    private int topK;
    private int maxTokens;
    private List<EvaluationResult> evaluations;
    private Scores averageScores;
}
