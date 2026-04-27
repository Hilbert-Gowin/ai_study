package com.example.ai.aistudy.chunk;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * 简单的固定长度文本切分器
 * 按字符数切分文本，保留 chunk 间上下文连贯性
 */
@Component
public class SimpleTextChunker {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP = 50;

    private final int chunkSize;
    private final int overlap;

    public SimpleTextChunker() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public SimpleTextChunker(int chunkSize, int overlap) {
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be less than chunkSize");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    /**
     * 将文本切分为多个 chunk
     */
    public List<String> chunk(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += chunkSize - overlap;
            if (start >= text.length()) break;
        }

        return chunks;
    }

    /**
     * 将多个文本切分并返回带元数据的 Document 列表
     */
    public List<org.springframework.ai.document.Document> chunkToDocuments(List<String> texts) {
        List<org.springframework.ai.document.Document> documents = new ArrayList<>();
        for (String text : texts) {
            List<String> chunks = chunk(text);
            for (int i = 0; i < chunks.size(); i++) {
                org.springframework.ai.document.Document doc = new org.springframework.ai.document.Document(chunks.get(i));
                doc.getMetadata().put("chunkIndex", i);
                doc.getMetadata().put("chunkTotal", chunks.size());
                doc.getMetadata().put("sourceText", text.length() > 50 ? text.substring(0, 50) + "..." : text);
                documents.add(doc);
            }
        }
        return documents;
    }

    public String getDescription() {
        return String.format("SimpleTextChunker(chunkSize=%d, overlap=%d)", chunkSize, overlap);
    }
}