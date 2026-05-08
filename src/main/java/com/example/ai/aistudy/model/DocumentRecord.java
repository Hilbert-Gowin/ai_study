package com.example.ai.aistudy.model;

import java.time.LocalDateTime;

/**
 * 上传文档的持久化记录，存 SQLite
 */
public class DocumentRecord {
    private Long id;
    private String filename;
    private String contentType;
    private String originalText; // 解析后的完整文本
    private String chunks;       // JSON 数组，存储所有 chunk 文本
    private int chunkCount;       // chunk 数量
    private LocalDateTime uploadTime;

    public DocumentRecord() {}

    public DocumentRecord(String filename, String contentType, String originalText, String chunks, int chunkCount) {
        this.filename = filename;
        this.contentType = contentType;
        this.originalText = originalText;
        this.chunks = chunks;
        this.chunkCount = chunkCount;
        this.uploadTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getOriginalText() { return originalText; }
    public void setOriginalText(String originalText) { this.originalText = originalText; }

    public String getChunks() { return chunks; }
    public void setChunks(String chunks) { this.chunks = chunks; }

    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }

    public LocalDateTime getUploadTime() { return uploadTime; }
    public void setUploadTime(LocalDateTime uploadTime) { this.uploadTime = uploadTime; }
}