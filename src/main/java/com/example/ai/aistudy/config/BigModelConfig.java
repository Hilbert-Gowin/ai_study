package com.example.ai.aistudy.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.Embedding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
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

    @Value("${spring.ai.openai.embedding.type:local}")
    private String embeddingType;

    @Value("${spring.ai.dashscope.api-key:}")
    private String dashscopeApiKey;

    @Value("${spring.ai.dashscope.embedding.model:text-embedding-v1}")
    private String dashscopeEmbeddingModel;

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
            return 1024;
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

    /**
     * 本地模拟 Embedding 模型 - 用于本地测试
     *
     * 工作原理：
     * 1. 预先为常见词汇分配随机向量
     * 2. 计算文本向量时，对文本中的词向量取平均
     * 3. 搜索时计算余弦相似度
     */
    public static class InMemoryEmbeddingModel implements EmbeddingModel {
        private static final int DIMENSIONS = 1024;
        private final Map<String, float[]> vocabulary = new HashMap<>();

        public InMemoryEmbeddingModel() {
            initVocabulary();
        }

        private void initVocabulary() {
            // ============ 技术/框架 ============
            addWord("AI", 0.8f, 0.6f, 0.9f);
            addWord("人工智能", 0.8f, 0.6f, 0.9f);
            addWord("框架", 0.6f, 0.4f, 0.5f);
            addWord("Spring", 0.5f, 0.3f, 0.4f);
            addWord("SpringBoot", 0.55f, 0.35f, 0.45f);
            addWord("Boot", 0.5f, 0.3f, 0.4f);
            addWord("应用", 0.5f, 0.4f, 0.5f);

            // ============ Java/编程语言 ============
            addWord("Java", 0.9f, 0.8f, 0.7f);
            addWord("编程", 0.7f, 0.6f, 0.6f);
            addWord("语言", 0.6f, 0.5f, 0.5f);
            addWord("代码", 0.5f, 0.5f, 0.5f);
            addWord("程序员", 0.6f, 0.5f, 0.5f);
            addWord("开发", 0.5f, 0.4f, 0.5f);
            addWord("开发人员", 0.5f, 0.4f, 0.5f);

            // ============ 数据库 ============
            addWord("数据库", 0.85f, 0.75f, 0.65f);
            addWord("DB", 0.8f, 0.7f, 0.6f);
            addWord("MySQL", 0.9f, 0.8f, 0.7f);
            addWord("SQL", 0.85f, 0.75f, 0.65f);
            addWord("连接", 0.6f, 0.5f, 0.5f);
            addWord("存储", 0.5f, 0.5f, 0.5f);
            addWord("查询", 0.6f, 0.6f, 0.5f);
            addWord("表", 0.5f, 0.5f, 0.4f);
            addWord("数据", 0.7f, 0.6f, 0.6f);

            // ============ RAG/检索增强 ============
            addWord("RAG", 0.9f, 0.8f, 0.7f);
            addWord("检索", 0.7f, 0.8f, 0.6f);
            addWord("增强", 0.6f, 0.7f, 0.5f);
            addWord("生成", 0.5f, 0.6f, 0.7f);
            addWord("检索增强生成", 0.85f, 0.75f, 0.65f);

            // ============ Embedding/向量 ============
            addWord("Embedding", 0.9f, 0.7f, 0.8f);
            addWord("向量化", 0.8f, 0.7f, 0.8f);
            addWord("文本", 0.5f, 0.5f, 0.5f);
            addWord("向量", 0.7f, 0.6f, 0.7f);
            addWord("语义", 0.6f, 0.8f, 0.6f);
            addWord("相似度", 0.6f, 0.7f, 0.7f);
            addWord("维度", 0.4f, 0.5f, 0.4f);

            // ============ 向量数据库 ============
            addWord("向量库", 0.7f, 0.6f, 0.7f);
            addWord("Milvus", 0.3f, 0.4f, 0.3f);
            addWord("Chroma", 0.3f, 0.4f, 0.3f);

            // ============ LLM/大模型 ============
            addWord("LLM", 0.8f, 0.7f, 0.8f);
            addWord("大模型", 0.8f, 0.7f, 0.8f);
            addWord("语言模型", 0.7f, 0.6f, 0.7f);
            addWord("智谱", 0.5f, 0.4f, 0.5f);
            addWord("GLM", 0.5f, 0.4f, 0.5f);
            addWord("GPT", 0.6f, 0.5f, 0.6f);
            addWord("模型", 0.6f, 0.5f, 0.6f);

            // ============ 天气/生活 ============
            addWord("天气", 0.2f, 0.85f, 0.3f);
            addWord("气候", 0.2f, 0.8f, 0.3f);
            addWord("温度", 0.1f, 0.7f, 0.2f);
            addWord("晴", 0.1f, 0.75f, 0.2f);
            addWord("雨", 0.1f, 0.7f, 0.25f);
            addWord("阴天", 0.1f, 0.8f, 0.2f);
            addWord("今天", 0.3f, 0.5f, 0.4f);
            addWord("查询", 0.6f, 0.6f, 0.5f);
            addWord("怎么样", 0.2f, 0.4f, 0.3f);

            // ============ 通用动词/虚词 ============
            addWord("是", 0.1f, 0.1f, 0.1f);
            addWord("一个", 0.1f, 0.1f, 0.1f);
            addWord("用于", 0.2f, 0.2f, 0.2f);
            addWord("的", 0.05f, 0.05f, 0.05f);
            addWord("如何", 0.4f, 0.3f, 0.4f);
            addWord("怎么", 0.3f, 0.3f, 0.3f);
            addWord("操作", 0.5f, 0.4f, 0.5f);
            addWord("技术", 0.4f, 0.5f, 0.4f);
            addWord("方法", 0.3f, 0.4f, 0.3f);
            addWord("系统", 0.4f, 0.3f, 0.4f);
            addWord("学习", 0.3f, 0.4f, 0.3f);
        }

        private void addWord(String word, float v1, float v2, float v3) {
            float[] vec = new float[DIMENSIONS];
            vec[0] = v1;
            vec[1] = v2;
            vec[2] = v3;
            for (int i = 3; i < DIMENSIONS; i++) {
                vec[i] = (float) (Math.random() * 0.1);
            }
            vocabulary.put(word, vec);
        }

        @Override
        public float[] embed(String text) {
            return embedText(text);
        }

        @Override
        public float[] embed(Document document) {
            return embed(document.getText());
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            return texts.stream().map(this::embed).toList();
        }

        @Override
        public List<float[]> embed(List<Document> documents,
                                    EmbeddingOptions options,
                                    BatchingStrategy batchingStrategy) {
            return documents.stream().map(this::embed).toList();
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
            return DIMENSIONS;
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

        private float[] embedText(String text) {
            float[] result = new float[DIMENSIONS];
            int count = 0;

            String[] words = text.split("\\s");

            for (String word : words) {
                if (word.isEmpty()) continue;

                float[] wordVec = findBestMatch(word);
                if (wordVec != null) {
                    for (int i = 0; i < DIMENSIONS; i++) {
                        result[i] += wordVec[i];
                    }
                    count++;
                }
            }

            if (count > 0) {
                for (int i = 0; i < DIMENSIONS; i++) {
                    result[i] /= count;
                }
            }

            return result;
        }

        private float[] findBestMatch(String word) {
            if (vocabulary.containsKey(word)) {
                return vocabulary.get(word);
            }

            for (Map.Entry<String, float[]> entry : vocabulary.entrySet()) {
                if (word.contains(entry.getKey()) || entry.getKey().contains(word)) {
                    return entry.getValue();
                }
            }

            float[] random = new float[DIMENSIONS];
            for (int i = 0; i < DIMENSIONS; i++) {
                random[i] = (float) (Math.random() * 0.2);
            }
            return random;
        }

        public static float cosineSimilarity(float[] a, float[] b) {
            float dotProduct = 0;
            float normA = 0;
            float normB = 0;
            for (int i = 0; i < a.length; i++) {
                dotProduct += a[i] * b[i];
                normA += a[i] * a[i];
                normB += b[i] * b[i];
            }
            if (normA == 0 || normB == 0) return 0;
            return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
        }
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        if ("zhipu".equalsIgnoreCase(embeddingType)) {
            return new ZhipuEmbeddingModel(apiKey, baseUrl, embeddingModel);
        } else if ("dashscope".equalsIgnoreCase(embeddingType)) {
            DashScopeApi dashScopeApi =
                DashScopeApi.builder()
                    .apiKey(dashscopeApiKey)
                    .build();
            return DashScopeEmbeddingModel.builder()
                    .dashScopeApi(dashScopeApi)
                    .build();
        }
        return new InMemoryEmbeddingModel();
    }

    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultSystem("你是一个乐于助人的AI助手，请用中文回答问题。").build();
    }
}