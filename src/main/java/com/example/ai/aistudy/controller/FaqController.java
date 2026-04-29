package com.example.ai.aistudy.controller;

import com.example.ai.aistudy.model.*;
import com.example.ai.aistudy.service.FaqService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/faq")
public class FaqController {

    private final FaqService faqService;

    public FaqController(FaqService faqService) {
        this.faqService = faqService;
    }

    /**
     * 添加FAQ
     * POST /api/faq/add
     * Body: {"question": "...", "answer": "..."}
     */
    @PostMapping("/add")
    public FaqAddResponse addFaq(@RequestBody FaqRequest request) {
        return faqService.addFaq(request.getQuestion(), request.getAnswer());
    }

    /**
     * FAQ问答
     * POST /api/faq/ask
     * Body: {"question": "..."}
     */
    @PostMapping("/ask")
    public FaqAskResponse askFaq(@RequestBody FaqRequest request) {
        return faqService.askFaq(request.getQuestion());
    }

    /**
     * 验证模式：返回检索详情
     * GET /api/faq/verify?query=xxx&topK=3
     */
    @GetMapping("/verify")
    public VerifyResponse verify(@RequestParam String query,
                                  @RequestParam(defaultValue = "3") int topK) {
        return faqService.verifyRetrieval(query, topK);
    }
}
