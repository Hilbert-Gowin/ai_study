package com.example.ai.aistudy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Rerank 调试信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RerankDetail {
    private String textPreview;
    private double vectorScore;
    private double keywordScore;
    private double exactMatchBonus;
    private double lengthBonus;
    private double totalScore;
    private int originalRank;
}
