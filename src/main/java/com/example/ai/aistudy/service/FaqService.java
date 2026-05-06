package com.example.ai.aistudy.service;

import com.example.ai.aistudy.chunk.SimpleTextChunker;
import com.example.ai.aistudy.config.RerankConfig;
import com.example.ai.aistudy.model.*;
import com.example.ai.aistudy.rerank.RerankService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class FaqService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final SimpleTextChunker textChunker;
    private final RerankService rerankService;
    private final RerankConfig rerankConfig;

    public FaqService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder,
                      EmbeddingModel embeddingModel, SimpleTextChunker textChunker,
                      RerankService rerankService, RerankConfig rerankConfig) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
        this.textChunker = textChunker;
        this.rerankService = rerankService;
        this.rerankConfig = rerankConfig;
    }

    /**
     * 添加FAQ到向量库
     */
    public FaqAddResponse addFaq(String question, String answer) {
        String content = "问题: " + question + "\n答案: " + answer;

        float[] embedding = embeddingModel.embed(question);
        System.out.println("=== FAQ 添加 ===");
        System.out.println("问题: " + question);
        System.out.println("Embedding 维度: " + embedding.length);
        System.out.println("Embedding (前5维): " + Arrays.toString(Arrays.copyOfRange(embedding, 0, Math.min(5, embedding.length))));
        System.out.println();

        List<Document> documents = textChunker.chunkToDocuments(List.of(content));

        List<ChunkInfo> chunkInfos = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            chunkInfos.add(new ChunkInfo(
                    i,
                    doc.getText().substring(0, Math.min(80, doc.getText().length())) + "...",
                    (String) doc.getMetadata().get("charRange")
            ));
        }

        vectorStore.add(documents);
        return new FaqAddResponse(true, "FAQ添加成功，共切分 " + documents.size() + " 个chunk", chunkInfos);
    }

    /**
     * FAQ问答，返回答案和来源
     */
    public FaqAskResponse askFaq(String question) {
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
                .reduce("", (a, b) -> a + "\n---\n" + b);

        List<ChunkSource> sources = documents.stream()
                .map(doc -> {
                    double score = getSimilarityScore(doc);
                    return new ChunkSource(
                            (Integer) doc.getMetadata().getOrDefault("chunkIndex", 0),
                            doc.getText().substring(0, Math.min(100, doc.getText().length())) + "...",
                            score,
                            1.0 - score
                    );
                })
                .collect(Collectors.toList());

        if (documents.isEmpty()) {
            return new FaqAskResponse("根据现有文档无法确定答案，请查阅原始资料", List.of());
        }

        String prompt = """
                你是一个FAQ问答助手。请根据【参考文档】回答用户的问题。

                指南：
                - 仔细阅读参考文档，找到与问题相关的内容
                - 如果参考文档中有相关信息，直接基于这些内容回答
                - 如果参考文档中的信息不完整，可以合理推断但要说明
                - 只有当参考文档完全不包含任何相关信息时，才回答"根据文档无法确定"

                【参考文档】
                %s

                【用户问题】
                %s
                """.formatted(context, question);

        String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        return new FaqAskResponse(answer, sources);
    }

    /**
     * 验证模式：只返回检索结果，不经过LLM
     */
    public VerifyResponse verifyRetrieval(String query, int topK) {
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

        List<ChunkSource> retrievals = documents.stream()
                .map(doc -> {
                    double score = getSimilarityScore(doc);
                    return new ChunkSource(
                            (Integer) doc.getMetadata().getOrDefault("chunkIndex", 0),
                            doc.getText().substring(0, Math.min(150, doc.getText().length())) + "...",
                            score,
                            1.0 - score
                    );
                })
                .collect(Collectors.toList());

        System.out.println("=== Verify 检索结果 ===");
        System.out.println("查询: " + query);
        System.out.println("检索到 " + retrievals.size() + " 个chunk:");
        for (int i = 0; i < retrievals.size(); i++) {
            ChunkSource cs = retrievals.get(i);
            System.out.printf("  [%d] score=%.4f, distance=%.4f%n", i, cs.getScore(), cs.getDistance());
            System.out.println("      " + cs.getText());
        }
        System.out.println();

        return new VerifyResponse(query, retrievals);
    }

    private double getSimilarityScore(Document doc) {
        Object distance = doc.getMetadata().get("distance");
        if (distance != null) {
            return 1.0 - ((Number) distance).doubleValue();
        }
        return 0.5;
    }
}
