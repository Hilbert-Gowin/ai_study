package com.example.ai.aistudy.controller;

import com.example.ai.aistudy.model.RagVerifyResponse;
import com.example.ai.aistudy.service.RagService;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * 添加文档
     * POST /api/rag/documents
     * Body: {"texts": ["文档内容1", "文档内容2"]}
     */
    @PostMapping("/documents")
    public Map<String, String> addDocuments(@RequestBody Map<String, List<String>> request) {
        List<String> texts = request.get("texts");
        ragService.addDocuments(texts);
        return Map.of("message", "成功添加 " + texts.size() + " 条文档");
    }

    /**
     * 检索文档
     * GET /api/rag/search?query=xxx&topK=3
     */
    @GetMapping("/search")
    public List<Document> search(@RequestParam String query,
                                 @RequestParam(defaultValue = "3") int topK) {
        return ragService.search(query, topK);
    }

    /**
     * RAG 问答
     * POST /api/rag/ask
     * Body: {"question": "什么是Spring AI?"}
     */
    @PostMapping("/ask")
    public Map<String, String> ask(@RequestBody Map<String, String> request) {
        String question = request.get("question");
        String answer = ragService.ragAsk(question);
        return Map.of("question", question, "answer", answer);
    }

    /**
     * RAG 校验：完整展示两阶段检索流程
     * GET /api/rag/verify?query=xxx&topK=3
     *
     * 返回内容：
     * - query: 查询内容
     * - rerankEnabled: 是否启用了 rerank
     * - coarseResults: 初筛结果（向量检索召回的候选）
     * - finalResults: 重排后的最终结果
     * - rerankDetails: 每条候选的详细评分（仅 rerank 开启时有）
     */
    @GetMapping("/verify")
    public RagVerifyResponse verify(@RequestParam String query,
                                    @RequestParam(defaultValue = "3") int topK) {
        return ragService.verifyRetrievalFull(query, topK);
    }

    /**
     * 初始化测试文档
     * POST /api/rag/init
     */
    @PostMapping("/init")
    public Map<String, Object> initDocuments() {
        List<String> testDocs = List.of(
            "Spring AI 是 Spring 官方推出的 AI 框架，旨在简化 AI 应用的开发。它提供了与各种 AI 模型交互的统一抽象，包括 OpenAI、Azure OpenAI、Anthropic、HuggingFace 等。",
            "EmbeddingModel 是 Spring AI 中的核心接口之一，用于将文本转换为向量表示。它支持多种嵌入模型，如 OpenAIEmbeddingModel、MinimalEmbeddingModel 等。",
            "VectorStore 是 Spring AI 中的向量存储接口，用于存储和检索向量。它支持多种向量数据库，如 Milvus、Chroma、PgVector 等，也支持内存存储 SimpleVectorStore。",
            "RerankService 是用于对检索结果进行重排的服务。它结合向量相似度、关键词匹配、精确匹配奖励等因素对候选文档进行排序，提高最终结果的相关性。",
            "ChatClient 是 Spring AI 提供的对话客户端，支持流式输出和非流式输出。它可以与各种 AI 模型集成，提供统一的对话接口。"
        );
        ragService.addDocuments(testDocs);
        return Map.of("message", "成功加载 " + testDocs.size() + " 条测试文档", "count", testDocs.size());
    }
}
