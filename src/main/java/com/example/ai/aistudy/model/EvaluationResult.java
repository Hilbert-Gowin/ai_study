package com.example.ai.aistudy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResult {
    private String question;
    private String answer;
    private List<ChunkSource> retrievalSources;
    private Scores scores;
}
