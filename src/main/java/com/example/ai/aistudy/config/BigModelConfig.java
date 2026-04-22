package com.example.ai.aistudy.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
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

    @Value("${spring.ai.openai.embedding.model:embedding-2}")
    private String embeddingModel;

    @Bean
    @Primary
    public ChatModel bigModelChatModel() {
        return new SimpleChatModel(apiKey, baseUrl, model);
    }

    public static class SimpleChatModel implements ChatModel {
        private final String apiKey;
        private final String baseUrl;
        private final String model;
        private final RestClient restClient;

        public SimpleChatModel(String apiKey, String baseUrl, String model) {
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.model = model;
            this.restClient = RestClient.builder()
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
            ChatResponse response = call(prompt);
            return Flux.just(response);
        }

        @Override
        public Flux<String> stream(String text) {
            String response = call(text);
            return Flux.just(response);
        }
    }

    /**
     * 智谱 AI Embedding 模型实现
     */
    public static class ZhipuEmbeddingModel implements EmbeddingModel {
        private final String apiKey;
        private final String baseUrl;
        private final String model;
        private final RestClient restClient;

        public ZhipuEmbeddingModel(String apiKey, String baseUrl, String model) {
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.model = model;
            this.restClient = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("Content-Type", "application/json")
                    .build();
        }

        @Override
        public float[] embed(String text) {
            Map<String, Object> request = Map.of(
                    "model", model,
                    "input", text
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/embeddings")
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            return parseEmbeddingResponse(response);
        }

        @Override
        public float[] embed(Document document) {
            return embed(document.getText());
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            Map<String, Object> request = Map.of(
                    "model", model,
                    "input", texts
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/embeddings")
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            return parseEmbeddingsResponse(response);
        }

        @Override
        public List<float[]> embed(List<Document> documents,
                                    EmbeddingOptions options,
                                    BatchingStrategy batchingStrategy) {
            List<String> texts = documents.stream()
                    .map(Document::getText)
                    .toList();
            return embed(texts);
        }

        @Override
        public EmbeddingResponse embedForResponse(List<String> texts) {
            List<float[]> embeddings = embed(texts);
            List<Embedding> results = new ArrayList<>();
            for (int i = 0; i < embeddings.size(); i++) {
                results.add(new Embedding(embeddings.get(i), i));
            }
            return new EmbeddingResponse(results, new EmbeddingResponseMetadata());
        }

        @Override
        public int dimensions() {
            return 1024; // 智谱 embedding-2 模型维度
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<String> texts = request.getInstructions();
            List<float[]> embeddings = embed(texts);
            List<Embedding> results = new ArrayList<>();
            for (int i = 0; i < embeddings.size(); i++) {
                results.add(new Embedding(embeddings.get(i), i));
            }
            return new EmbeddingResponse(results, new EmbeddingResponseMetadata());
        }

        private float[] parseEmbeddingResponse(Map<String, Object> response) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data != null && !data.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<Number> embedding = (List<Number>) data.get(0).get("embedding");
                float[] result = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    result[i] = embedding.get(i).floatValue();
                }
                return result;
            }
            return new float[0];
        }

        private List<float[]> parseEmbeddingsResponse(Map<String, Object> response) {
            List<float[]> result = new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data != null) {
                for (Map<String, Object> item : data) {
                    @SuppressWarnings("unchecked")
                    List<Number> embedding = (List<Number>) item.get("embedding");
                    float[] emb = new float[embedding.size()];
                    for (int i = 0; i < embedding.size(); i++) {
                        emb[i] = embedding.get(i).floatValue();
                    }
                    result.add(emb);
                }
            }
            return result;
        }
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return new ZhipuEmbeddingModel(apiKey, baseUrl, embeddingModel);
    }

    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultSystem("你是一个乐于助人的AI助手，请用中文回答问题。").build();
    }
}