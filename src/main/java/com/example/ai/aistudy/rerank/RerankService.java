package com.example.ai.aistudy.rerank;

import com.example.ai.aistudy.config.RerankConfig;
import org.ansj.domain.Result;
import org.ansj.domain.Term;
import org.ansj.splitWord.analysis.ToAnalysis;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Rerank 服务：对候选文档进行多维度重排
 *
 * 评分维度：
 * - 向量相似度（来自初筛结果）
 * - BM25 关键词匹配度
 * - 精确匹配奖励
 * - 长度偏好奖励
 */
@Service
public class RerankService {

    public record RerankResult(Document document, RerankScore score, int originalRank) {}

    private final RerankConfig config;

    public RerankService(RerankConfig config) {
        this.config = config;
    }

    /**
     * 对候选文档进行重排
     */
    public List<Document> rerank(String query, List<Document> candidates, int topK) {
        return rerankWithDetails(query, candidates, topK).stream()
                .map(RerankResult::document)
                .collect(Collectors.toList());
    }

    /**
     * 对候选文档进行重排，返回详细信息（含原始排名）
     */
    public List<RerankResult> rerankWithDetails(String query, List<Document> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<RerankResult> results = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            Document doc = candidates.get(i);
            RerankScore score = calculateScore(query, doc);
            results.add(new RerankResult(doc, score, i + 1));
        }

        results.sort(Comparator.comparingDouble((RerankResult r) -> r.score().totalScore()).reversed());

        printRerankDebug(query, results);

        return results.stream().limit(topK).toList();
    }

    private RerankScore calculateScore(String query, Document doc) {
        double vectorScore = extractVectorScore(doc);
        double keywordScore = calculateBM25(query, doc.getText());
        double exactMatchBonus = calculateExactMatch(query, doc.getText());
        double lengthBonus = calculateLengthBonus(doc.getText());

        double total = vectorScore * config.getVectorWeight()
                + keywordScore * config.getKeywordWeight()
                + exactMatchBonus * config.getExactMatchWeight()
                + lengthBonus * config.getLengthWeight();

        return new RerankScore(doc, vectorScore, keywordScore, exactMatchBonus, lengthBonus, total);
    }

    private double extractVectorScore(Document doc) {
        Object distance = doc.getMetadata().get("distance");
        if (distance != null) {
            double dist = ((Number) distance).doubleValue();
            return Math.max(0.0, 1.0 - dist);
        }
        return 0.5;
    }

    /**
     * 使用 ansj_seg 中文分词计算 BM25
     */
    private double calculateBM25(String query, String text) {
        List<String> queryTerms = tokenize(query);
        List<String> docTerms = tokenize(text);
        if (queryTerms.isEmpty() || docTerms.isEmpty()) return 0.0;

        double score = 0.0;
        int docLen = docTerms.size();
        double avgLen = 500;
        double k1 = 1.5;
        double b = 0.75;

        for (String term : queryTerms) {
            long tf = docTerms.stream().filter(t -> t.equals(term)).count();
            if (tf > 0) {
                double idf = Math.log((avgLen + 1) / (tf + 1) + 1);
                double tfComponent = (tf * (k1 + 1)) / (tf + k1 * (1 - b + b * docLen / avgLen));
                score += idf * tfComponent;
            }
        }
        return Math.min(1.0, score / queryTerms.size());
    }

    /**
     * 精确匹配奖励：查询完整词组出现在文档中
     */
    private double calculateExactMatch(String query, String text) {
        // 查询完整出现在文档中
        if (text.contains(query)) {
            return config.getExactMatchWeight();
        }

        // 查询分词后，每个词在文档中出现的人数
        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) return 0.0;

        List<String> docTerms = tokenize(text);
        long matchCount = queryTerms.stream().filter(docTerms::contains).count();
        return (double) matchCount / queryTerms.size() * config.getExactMatchWeight();
    }

    /**
     * 长度偏好：适中长度奖励，过短/过长惩罚
     * 目标长度 200-800 字符
     */
    private double calculateLengthBonus(String text) {
        int len = text.length();
        if (len >= 200 && len <= 800) return config.getLengthWeight();
        if (len < 100) return -config.getLengthWeight() * 0.5;
        if (len > 1500) return -config.getLengthWeight() * 0.3;
        if (len < 200) return config.getLengthWeight() * 0.5;
        return config.getLengthWeight() * 0.7;
    }

    /**
     * 使用 ansj_seg 分词，过滤停用词和过短词汇
     */
    private List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();

        Result result = ToAnalysis.parse(text);
        Set<String> stopWords = Set.of(
                "的", "了", "在", "是", "我", "有", "和", "就", "不", "人",
                "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去",
                "你", "会", "着", "没有", "看", "好", "自己", "这", "那", "它",
                "他", "她", "吗", "呢", "吧", "啊", "哦", "嗯", "噢", "呀",
                "只", "只是", "而", "但", "却", "又", "与", "及", "等", "或",
                "之", "以", "为", "于", "而且", "所以", "因为", "如果", "虽然"
        );

        return result.getTerms().stream()
                .map(Term::getName)
                .filter(t -> t.length() > 1 && !stopWords.contains(t))
                .collect(Collectors.toList());
    }

    private void printRerankDebug(String query, List<RerankResult> results) {
        System.out.println("=== Rerank Debug ===");
        System.out.println("查询: " + query);
        System.out.println("候选数: " + results.size());
        System.out.printf("%-5s %-8s %-8s %-8s %-8s %-8s %-8s%n",
                "#", "原排名", "向量分", "关键词分", "精确分", "长度分", "总分");
        for (int i = 0; i < results.size(); i++) {
            RerankResult r = results.get(i);
            RerankScore s = r.score();
            String preview = s.document().getText().substring(0, Math.min(20, s.document().getText().length()));
            System.out.printf("%-5d %-8d %.4f   %.4f   %.4f   %.4f   %.4f  [%s...]%n",
                    i + 1, r.originalRank(), s.vectorScore(), s.keywordScore(), s.exactMatchBonus(),
                    s.lengthBonus(), s.totalScore(), preview);
        }
        System.out.println();
    }

    public RerankConfig getConfig() {
        return config;
    }
}
