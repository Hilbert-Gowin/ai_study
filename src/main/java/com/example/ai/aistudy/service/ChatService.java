package com.example.ai.aistudy.service;

import com.example.ai.aistudy.model.Conversation;
import com.example.ai.aistudy.model.Message;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatService {

    private final ChatClient chatClient;

    // 会话存储
    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();

    public ChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        你是一个专业的 AI 技术助手，专注于回答 Java、Spring Boot 和 Spring AI 相关的开发问题。
                        请遵守以下规则：
                        1. 始终用中文回答；
                        2. 代码示例优先给出可直接运行的完整版本；
                        3. 如果问题超出技术范畴，礼貌说明并引导回技术话题；
                        4. 回答简洁，避免冗余解释。
                        """)
                .build();
    }

    /**
     * 简单对话 - 流式输出
     */
    public Flux<String> chatStream(String message) {
        StringBuilder fullResponse = new StringBuilder();

        Flux<String> flux = chatClient.prompt()
                .user(message)
                .stream()
                .content();

        // 保存会话
        Conversation conv = new Conversation();
        String convId = "simple-" + System.currentTimeMillis();
        conv.setId(convId);
        conv.setTitle(message.length() > 20 ? message.substring(0, 20) + "..." : message);
        conv.getMessages().add(new Message("user", message, System.currentTimeMillis()));
        conv.setCreatedAt(System.currentTimeMillis());
        conv.setUpdatedAt(System.currentTimeMillis());

        return flux
                .doOnNext(chunk -> fullResponse.append(chunk))
                .doOnComplete(() -> {
                    conv.getMessages().add(new Message("assistant", fullResponse.toString(), System.currentTimeMillis()));
                    conv.setUpdatedAt(System.currentTimeMillis());
                    conversations.put(convId, conv);
                });
    }

    /**
     * 带会话记忆的对话 - 流式输出
     */
    public Flux<String> chatWithMemoryStream(String conversationId, String message) {
        Conversation conv = conversations.computeIfAbsent(conversationId, id -> {
            Conversation newConv = new Conversation();
            newConv.setId(id);
            newConv.setTitle(message.length() > 20 ? message.substring(0, 20) + "..." : message);
            newConv.setCreatedAt(System.currentTimeMillis());
            return newConv;
        });

        conv.getMessages().add(new Message("user", message, System.currentTimeMillis()));
        conv.setUpdatedAt(System.currentTimeMillis());

        StringBuilder fullResponse = new StringBuilder();

        return chatClient.prompt()
                .user(message)
                .stream()
                .content()
                .doOnNext(chunk -> fullResponse.append(chunk))
                .doOnComplete(() -> {
                    conv.getMessages().add(new Message("assistant", fullResponse.toString(), System.currentTimeMillis()));
                    conv.setUpdatedAt(System.currentTimeMillis());
                });
    }

    /**
     * 获取所有会话列表
     */
    public Map<String, Conversation> getAllConversations() {
        return conversations;
    }

    /**
     * 获取指定会话的消息
     */
    public Conversation getConversation(String conversationId) {
        return conversations.get(conversationId);
    }

    /**
     * 删除会话
     */
    public void deleteConversation(String conversationId) {
        conversations.remove(conversationId);
    }

    /**
     * 简单对话
     */
    public String chat(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    /**
     * 带会话记忆的对话
     */
    public String chatWithMemory(String conversationId, String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
