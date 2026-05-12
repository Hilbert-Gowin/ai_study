package com.example.ai.aistudy.service;

import com.example.ai.aistudy.chunk.SimpleTextChunker;
import com.example.ai.aistudy.config.BigModelConfig.InMemoryEmbeddingModel;
import com.example.ai.aistudy.config.RerankConfig;
import com.example.ai.aistudy.model.ChunkSource;
import com.example.ai.aistudy.model.DocumentRecord;
import com.example.ai.aistudy.model.RagVerifyResponse;
import com.example.ai.aistudy.model.RerankDetail;
import com.example.ai.aistudy.repository.DocumentRepository;
import com.example.ai.aistudy.rerank.RerankScore;
import com.example.ai.aistudy.rerank.RerankService;
import com.example.ai.aistudy.rerank.RerankService.RerankResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
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
    private final DocumentRepository documentRepository;
    private final ObjectMapper objectMapper;

    public RagService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder,
                      EmbeddingModel embeddingModel, SimpleTextChunker textChunker,
                      RerankService rerankService, RerankConfig rerankConfig,
                      DocumentRepository documentRepository) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
        this.textChunker = textChunker;
        this.rerankService = rerankService;
        this.rerankConfig = rerankConfig;
        this.documentRepository = documentRepository;
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void reloadFromDatabase() {
        List<String> chunks = documentRepository.getAllChunks();
        if (!chunks.isEmpty()) {
            System.out.println("[RagService] 启动恢复：从数据库加载 " + chunks.size() + " 个 chunks");
            addDocumentsToVectorStore(chunks);
            System.out.println("[RagService] 启动恢复完成，向量库当前 chunk 数约 " + chunks.size());
        } else {
            System.out.println("[RagService] 启动恢复：数据库无记录，从空白开始");
        }
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

    /**
     * 上传文档时调用：持久化到 SQLite 并添加到向量库
     */
    public void addDocumentsWithPersistence(List<String> texts, String filename, String contentType) {
        // 生成 Document 对象（包含 metadata，chunk 已限制在 500 字符以内）
        List<Document> documents = textChunker.chunkToDocuments(texts);
        System.out.println("=== Chunking 结果 ===");
        System.out.println("原始文档数: " + texts.size());
        System.out.println("切分后 Chunk 数: " + documents.size());
        System.out.println("Chunk 策略: " + textChunker.getDescription());
        System.out.println();

        // 添加到向量库
        vectorStore.add(documents);

        // 持久化到 SQLite：从 Document 对象提取 chunk 文本
        List<String> allChunks = documents.stream()
                .map(Document::getText)
                .toList();

        String originalText = String.join("\n\n--- 文档分隔 ---\n\n", texts);
        try {
            String chunksJson = objectMapper.writeValueAsString(allChunks);
            DocumentRecord record = new DocumentRecord(filename, contentType, originalText, chunksJson, allChunks.size());
            documentRepository.save(record);
        } catch (JsonProcessingException e) {
            System.out.println("[RagService] 序列化 chunks 失败: " + e.getMessage());
        }
    }

    private void addDocumentsToVectorStore(List<String> chunks) {
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            org.springframework.ai.document.Document doc = new org.springframework.ai.document.Document(chunks.get(i));
            doc.getMetadata().put("chunkIndex", i);
            doc.getMetadata().put("chunkTotal", chunks.size());
            doc.getMetadata().put("charRange", "N/A");
            doc.getMetadata().put("sourceText", "恢复数据");
            documents.add(doc);
        }
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

    /**
     * 重建向量库：从数据库加载所有文档的 chunks
     */
    public void rebuildVectorStore() {
        List<String> chunks = documentRepository.getAllChunks();
        // 清空当前向量库（通过重建的方式）
        clearVectorStore();
        if (!chunks.isEmpty()) {
            addDocumentsToVectorStore(chunks);
        }
        System.out.println("[RagService] 向量库重建完成，当前 chunk 数: " + chunks.size());
    }

    /**
     * 清空向量库中的所有文档
     */
    public void clearVectorStore() {
        // SimpleVectorStore 不提供直接清空方法，通过反射或重建实现
        // 暂时通过重建空向量库处理
        try {
            // 获取 vectorStore 的 documents 字段并清空
            java.lang.reflect.Field field = vectorStore.getClass().getDeclaredField("documents");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Document> docs = (java.util.List<Document>) field.get(vectorStore);
            docs.clear();
            System.out.println("[RagService] 向量库已清空");
        } catch (Exception e) {
            System.out.println("[RagService] 清空向量库失败，将尝试重建方式: " + e.getMessage());
        }
    }

    /**
     * 删除文档：从数据库删除，并从向量库移除对应的 chunks
     * @param docId 文档 ID
     * @return 是否成功删除
     */
    public boolean deleteDocument(Long docId) {
        Optional<DocumentRecord> optDoc = documentRepository.findById(docId);
        if (optDoc.isEmpty()) {
            System.out.println("[RagService] 文档不存在: id=" + docId);
            return false;
        }

        DocumentRecord doc = optDoc.get();
        // 解析出该文档的 chunks
        List<String> docChunks;
        try {
            docChunks = objectMapper.readValue(doc.getChunks(), new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            System.out.println("[RagService] 解析 chunks 失败: " + e.getMessage());
            return false;
        }

        // 从数据库删除
        documentRepository.deleteById(docId);

        // 重建向量库
        rebuildVectorStore();

        System.out.println("[RagService] 删除文档: " + doc.getFilename() + ", 移除了 " + docChunks.size() + " 个 chunks");
        return true;
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
