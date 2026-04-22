package com.example.ai.aistudy.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
public class BigModelConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://open.bigmodel.cn/api/paas}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:glm-4-flash}")
    private String model;

    @Bean
    @Primary
    public ChatModel bigModelChatModel() {
        return new SimpleChatModel(apiKey, baseUrl, model);
    }

    public static class SimpleChatModel implements ChatModel {
        private final String apiKey;
        private final String baseUrl;
        private final String model;
        private final org.springframework.web.client.RestClient restClient;

        public SimpleChatModel(String apiKey, String baseUrl, String model) {
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.model = model;
            this.restClient = org.springframework.web.client.RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("Content-Type", "application/json")
                    .build();
        }

        private String extractContent(Map<String, Object> response) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices != null && !choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                return (String) message.get("content");
            }
            return "";
        }

        @Override
        public String call(String text) {
            Map<String, Object> request = Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", text))
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            return extractContent(response);
        }

        @Override
        public String call(org.springframework.ai.chat.messages.Message... messages) {
            List<Map<String, Object>> msgList = new ArrayList<>();
            for (var msg : messages) {
                msgList.add(Map.of("role", msg.getMessageType().name().toLowerCase(), "content", msg.getText()));
            }

            Map<String, Object> request = Map.of(
                    "model", model,
                    "messages", msgList
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            return extractContent(response);
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            List<Map<String, Object>> msgList = new ArrayList<>();

            for (var msg : prompt.getInstructions()) {
                msgList.add(Map.of("role", msg.getMessageType().name().toLowerCase(), "content", msg.getText()));
            }

            Map<String, Object> request = Map.of(
                    "model", model,
                    "messages", msgList
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            String content = extractContent(response);
            Generation generation = new Generation(new AssistantMessage(content));
            return new ChatResponse(List.of(generation));
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return ChatOptions.builder().model(model).build();
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            // 简单实现：返回单个完整响应作为流
            // 真正的流式需要处理 SSE，与当前 RestClient 方式不兼容
            ChatResponse response = call(prompt);
            return Flux.just(response);
        }

        @Override
        public Flux<String> stream(String text) {
            // 简单实现：返回单个完整响应作为流
            String response = call(text);
            return Flux.just(response);
        }
    }

    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultSystem("你是一个乐于助人的AI助手，请用中文回答问题。").build();
    }
}
