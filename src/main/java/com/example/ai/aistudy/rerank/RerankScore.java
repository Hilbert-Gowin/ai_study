package com.example.ai.aistudy.rerank;

/**
 * Rerank 评分结果记录
 */
public record RerankScore(
        org.springframework.ai.document.Document document,
        double vectorScore,
        double keywordScore,
        double exactMatchBonus,
        double lengthBonus,
        double totalScore
) {
}
