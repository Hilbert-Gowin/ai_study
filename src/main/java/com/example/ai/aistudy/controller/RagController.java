package com.example.ai.aistudy.controller;

import com.example.ai.aistudy.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

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
}
