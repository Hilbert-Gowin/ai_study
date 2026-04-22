package com.example.ai.aistudy.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

// @Service
// ToolService 需要额外配置以适配 Spring AI 1.1.x 的 ToolCallback API
// 当前已禁用以确保应用能正常启动
public class ToolService {

    private final ChatClient chatClient;

    public ToolService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 使用 Function Calling 进行对话
     */
    public String chatWithTools(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
