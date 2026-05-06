package com.example.ai.aistudy.service;

import com.example.ai.aistudy.chunk.SimpleTextChunker;
import com.example.ai.aistudy.config.BigModelConfig.InMemoryEmbeddingModel;
import com.example.ai.aistudy.config.RerankConfig;
import com.example.ai.aistudy.model.ChunkSource;
import com.example.ai.aistudy.model.RagVerifyResponse;
import com.example.ai.aistudy.model.RerankDetail;
import com.example.ai.aistudy.rerank.RerankScore;
import com.example.ai.aistudy.rerank.RerankService;
import com.example.ai.aistudy.rerank.RerankService.RerankResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final SimpleTextChunker textChunker;
    private final RerankService rerankService;
    private final RerankConfig rerankConfig;

    public RagService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder,
                      EmbeddingModel embeddingModel, SimpleTextChunker textChunker,
                      RerankService rerankService, RerankConfig rerankConfig) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
        this.textChunker = textChunker;
        this.rerankService = rerankService;
        this.rerankConfig = rerankConfig;
    }

    public void addDocuments(List<String> texts) {
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            float[] embedding = embeddingModel.embed(text);
            System.out.println("=== 文档 " + (i + 1) + " ===");
            System.out.println("内容: " + text);
            System.out.println("Embedding 维度: " + embedding.length);
            System.out.println("Embedding (前10维): " + toString(embedding));
            System.out.println();
        }

        if (texts.size() > 1) {
            float[][] embeddings = new float[texts.size()][];
            for (int i = 0; i < texts.size(); i++) {
                embeddings[i] = embeddingModel.embed(texts.get(i));
            }

            System.out.println("=== 文档相似度矩阵 ===");
            for (int i = 0; i < texts.size(); i++) {
                for (int j = i + 1; j < texts.size(); j++) {
                    float sim = InMemoryEmbeddingModel.cosineSimilarity(embeddings[i], embeddings[j]);
                    System.out.printf("文档%d vs 文档%d 相似度: %.4f%n", i + 1, j + 1, sim);
                }
            }
            System.out.println();
        }

        List<Document> documents = textChunker.chunkToDocuments(texts);
        System.out.println("=== Chunking 结果 ===");
        System.out.println("原始文档数: " + texts.size());
        System.out.println("切分后 Chunk 数: " + documents.size());
        System.out.println("Chunk 策略: " + textChunker.getDescription());
        System.out.println();

        vectorStore.add(documents);
    }

    private String toString(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        int limit = Math.min(10, arr.length);
        for (int i = 0; i < limit; i++) {
            sb.append(String.format("%.4f", arr[i]));
            if (i < limit - 1) sb.append(", ");
        }
        if (arr.length > 10) sb.append(", ...");
        sb.append("]");
        return sb.toString();
    }

    public List<Document> search(String query, int topK) {
        if (rerankConfig.isEnabled()) {
            List<Document> coarse = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(rerankConfig.getInitialTopK())
                            .similarityThreshold(0.3f)
                            .build()
            );
            return rerankService.rerank(query, coarse, topK);
        }
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(0.5f)
                        .build()
        );
    }

    public String ragAsk(String question) {
        List<Document> documents;
        if (rerankConfig.isEnabled()) {
            List<Document> coarse = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(question)
                            .topK(rerankConfig.getInitialTopK())
                            .similarityThreshold(0.3f)
                            .build()
            );
            documents = rerankService.rerank(question, coarse, rerankConfig.getFinalTopK());
        } else {
            documents = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(question)
                            .topK(3)
                            .similarityThreshold(0.5f)
                            .build()
            );
        }

        String context = documents.stream()
                .map(Document::getText)
                .reduce("", (a, b) -> a + "\n" + b);

        String prompt = """
                你是一个严谨的问答助手。请严格基于以下【参考文档】回答用户的问题。

                规则：
                - 只使用参考文档中明确提及的信息作答；
                - 如果参考文档中信息不足，直接回答"根据现有文档无法确定，建议查阅原始资料"；
                - 禁止推测或补充文档中未提及的内容；
                - 引用来源时，指明是第几段内容。

                【参考文档】
                %s

                【用户问题】
                %s
                """.formatted(context, question);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    public List<Map<String, Object>> verifyRetrieval(String query, int topK) {
        List<Document> documents;
        if (rerankConfig.isEnabled()) {
            List<Document> coarse = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(rerankConfig.getInitialTopK())
                            .similarityThreshold(0.3f)
                            .build()
            );
            documents = rerankService.rerank(query, coarse, topK);
        } else {
            documents = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(topK)
                            .similarityThreshold(0.3f)
                            .build()
            );
        }

        return documents.stream()
                .map(doc -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("text", doc.getText());
                    result.put("chunkIndex", doc.getMetadata().get("chunkIndex"));
                    result.put("chunkTotal", doc.getMetadata().get("chunkTotal"));
                    result.put("charRange", doc.getMetadata().get("charRange"));
                    result.put("sourceText", doc.getMetadata().get("sourceText"));

                    Object distance = doc.getMetadata().get("distance");
                    if (distance != null) {
                        double dist = ((Number) distance).doubleValue();
                        result.put("distance", dist);
                        result.put("score", 1.0 - dist);
                    }

                    return result;
                })
                .collect(Collectors.toList());
    }

    /**
     * 完整校验流程：返回初筛结果、重排结果、详细评分
     */
    public RagVerifyResponse verifyRetrievalFull(String query, int topK) {
        List<Document> coarse;
        List<ChunkSource> coarseResults;
        List<ChunkSource> finalResults;
        List<RerankDetail> rerankDetails;

        if (rerankConfig.isEnabled()) {
            coarse = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(rerankConfig.getInitialTopK())
                            .similarityThreshold(0.0f)
                            .build()
            );

            coarseResults = toChunkSources(coarse);

            List<RerankResult> reranked = rerankService.rerankWithDetails(query, coarse, topK);
            finalResults = toChunkSources(reranked.stream().map(RerankResult::document).toList());

            rerankDetails = reranked.stream()
                    .map(r -> {
                        RerankScore s = r.score();
                        String text = s.document().getText();
                        String preview = text.substring(0, Math.min(50, text.length()));
                        return new RerankDetail(
                                preview + (text.length() > 50 ? "..." : ""),
                                s.vectorScore(),
                                s.keywordScore(),
                                s.exactMatchBonus(),
                                s.lengthBonus(),
                                s.totalScore(),
                                r.originalRank()
                        );
                    })
                    .toList();
        } else {
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(topK)
                            .similarityThreshold(0.0f)
                            .build()
            );
            coarseResults = toChunkSources(docs);
            finalResults = coarseResults;
            rerankDetails = Collections.emptyList();
        }

        return new RagVerifyResponse(query, rerankConfig.isEnabled(), coarseResults, finalResults, rerankDetails);
    }

    private List<ChunkSource> toChunkSources(List<Document> docs) {
        return docs.stream()
                .map(doc -> {
                    double score = 0.5;
                    double distance = 0.5;
                    Object distObj = doc.getMetadata().get("distance");
                    if (distObj != null) {
                        distance = ((Number) distObj).doubleValue();
                        score = 1.0 - distance;
                    }
                    return new ChunkSource(
                            (Integer) doc.getMetadata().getOrDefault("chunkIndex", 0),
                            doc.getText().substring(0, Math.min(100, doc.getText().length())) + (doc.getText().length() > 100 ? "..." : ""),
                            score,
                            distance
                    );
                })
                .toList();
    }
}
