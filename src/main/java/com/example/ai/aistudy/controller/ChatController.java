package com.example.ai.aistudy.controller;

import com.example.ai.aistudy.model.Conversation;
import com.example.ai.aistudy.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 简单对话 - 流式
     * POST /api/chat/stream
     * Body: {"message": "你好"}
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        return chatService.chatStream(message);
    }

    /**
     * 带会话记忆的对话 - 流式
     * POST /api/chat/memory/stream
     * Body: {"conversationId": "user-001", "message": "你好"}
     */
    @PostMapping(value = "/memory/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatWithMemoryStream(@RequestBody Map<String, String> request) {
        String conversationId = request.get("conversationId");
        String message = request.get("message");
        return chatService.chatWithMemoryStream(conversationId, message);
    }

    /**
     * 获取所有会话列表
     * GET /api/chat/sessions
     */
    @GetMapping("/sessions")
    public Map<String, Conversation> getAllConversations() {
        return chatService.getAllConversations();
    }

    /**
     * 获取指定会话的消息历史
     * GET /api/chat/sessions/{id}
     */
    @GetMapping("/sessions/{id}")
    public Conversation getConversation(@PathVariable String id) {
        return chatService.getConversation(id);
    }

    /**
     * 删除会话
     * DELETE /api/chat/sessions/{id}
     */
    @DeleteMapping("/sessions/{id}")
    public Map<String, String> deleteConversation(@PathVariable String id) {
        chatService.deleteConversation(id);
        return Map.of("message", "deleted");
    }

    // 原有接口保留
    @PostMapping
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String reply = chatService.chat(message);
        return Map.of("reply", reply);
    }

    @PostMapping("/memory")
    public Map<String, String> chatWithMemory(@RequestBody Map<String, String> request) {
        String conversationId = request.get("conversationId");
        String message = request.get("message");
        String reply = chatService.chatWithMemory(conversationId, message);
        return Map.of("conversationId", conversationId, "reply", reply);
    }
}
