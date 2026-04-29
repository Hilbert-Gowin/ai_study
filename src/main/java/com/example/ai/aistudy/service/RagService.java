package com.example.ai.aistudy.service;

import com.example.ai.aistudy.chunk.SimpleTextChunker;
import com.example.ai.aistudy.config.BigModelConfig.InMemoryEmbeddingModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final SimpleTextChunker textChunker;

    public RagService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder, EmbeddingModel embeddingModel, SimpleTextChunker textChunker) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
        this.textChunker = textChunker;
    }

    /**
     * 添加文档到向量存储
     */
    public void addDocuments(List<String> texts) {
        // 先打印每个文本的 embedding
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            float[] embedding = embeddingModel.embed(text);
            System.out.println("=== 文档 " + (i + 1) + " ===");
            System.out.println("内容: " + text);
            System.out.println("Embedding 维度: " + embedding.length);
            System.out.println("Embedding (前10维): " + toString(embedding));
            System.out.println();
        }

        // 计算文档之间的相似度
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

        // 使用 chunking 策略切分文档
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

    /**
     * 根据查询检索相关文档
     */
    public List<Document> search(String query, int topK) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(0.5f)
                        .build()
        );
    }

    /**
     * RAG 问答：检索相关文档，然后结合上下文回答问题
     */
    public String ragAsk(String question) {
        // 1. 检索相关文档
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(3)
                        .similarityThreshold(0.5f)
                        .build()
        );

        // 2. 拼接上下文
        String context = documents.stream()
                .map(Document::getText)
                .reduce("", (a, b) -> a + "\n" + b);

        // 3. 带上下文提问
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

    /**
     * 验证模式：返回检索详情，不经过LLM，用于诊断检索是否正确
     */
    public List<Map<String, Object>> verifyRetrieval(String query, int topK) {
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(0.3f)
                        .build()
        );

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
}
