package com.example.ai.aistudy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RagService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 添加文档到向量存储
     */
    public void addDocuments(List<String> texts) {
        List<Document> documents = texts.stream()
                .map(Document::new)
                .toList();
        vectorStore.add(documents);
    }

    /**
     * 根据查询检索相关文档
     */
    public List<Document> search(String query, int topK) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .build()
        );
    }

    /**
     * RAG 问答：检索相关文档，然后结合上下文回答问题
     */
    public String ragAsk(String question) {
        // 1. 检索相关文档
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(3)
                        .build()
        );

        // 2. 拼接上下文
        String context = documents.stream()
                .map(Document::getText)
                .reduce("", (a, b) -> a + "\n" + b);

        // 3. 带上下文提问
        String prompt = """
                你是一个严谨的问答助手。请严格基于以下【参考文档】回答用户的问题。

                规则：
                - 只使用参考文档中明确提及的信息作答；
                - 如果参考文档中信息不足，直接回答"根据现有文档无法确定，建议查阅原始资料"；
                - 禁止推测或补充文档中未提及的内容；
                - 引用来源时，指明是第几段内容。

                【参考文档】
                %s

                【用户问题】
                %s
                """.formatted(context, question);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}
