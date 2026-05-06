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
}
