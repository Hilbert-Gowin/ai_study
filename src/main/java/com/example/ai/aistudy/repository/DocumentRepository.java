package com.example.ai.aistudy.repository;

import com.example.ai.aistudy.model.DocumentRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 文档记录 SQLite 持久化
 */
@Repository
public class DocumentRepository {

    private static final String DB_PATH = "data/documents.db";
    private final ObjectMapper objectMapper;

    public DocumentRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        // 确保目录存在
        java.nio.file.Path dir = java.nio.file.Path.of("data");
        if (!java.nio.file.Files.exists(dir)) {
            try {
                java.nio.file.Files.createDirectories(dir);
            } catch (Exception e) {
                throw new RuntimeException("创建 data 目录失败", e);
            }
        }
        // 建表
        String sql = """
            CREATE TABLE IF NOT EXISTS documents (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                filename TEXT NOT NULL,
                content_type TEXT,
                original_text TEXT,
                chunks TEXT,
                chunk_count INTEGER,
                upload_time TEXT
            )
            """;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("[DocumentRepository] SQLite 表初始化完成，路径: " + DB_PATH);
        } catch (SQLException e) {
            throw new RuntimeException("初始化 SQLite 表失败", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
    }

    public DocumentRecord save(DocumentRecord doc) {
        String sql = """
            INSERT INTO documents (filename, content_type, original_text, chunks, chunk_count, upload_time)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, doc.getFilename());
            ps.setString(2, doc.getContentType());
            ps.setString(3, doc.getOriginalText());
            ps.setString(4, doc.getChunks());
            ps.setInt(5, doc.getChunkCount());
            ps.setString(6, doc.getUploadTime().toString());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    doc.setId(rs.getLong(1));
                }
            }
            System.out.println("[DocumentRepository] 保存文档: " + doc.getFilename() + ", id=" + doc.getId() + ", chunks=" + doc.getChunkCount());
            return doc;
        } catch (SQLException e) {
            throw new RuntimeException("保存文档失败", e);
        }
    }

    public List<DocumentRecord> findAll() {
        String sql = "SELECT * FROM documents ORDER BY upload_time DESC";
        List<DocumentRecord> docs = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                docs.add(toRecord(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询文档失败", e);
        }
        return docs;
    }

    public Optional<DocumentRecord> findById(Long id) {
        String sql = "SELECT * FROM documents WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(toRecord(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询文档失败", e);
        }
        return Optional.empty();
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM documents WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
            System.out.println("[DocumentRepository] 删除文档 id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("删除文档失败", e);
        }
    }

    public void update(DocumentRecord doc) {
        String sql = """
            UPDATE documents SET filename = ?, content_type = ?, original_text = ?, chunks = ?, chunk_count = ?
            WHERE id = ?
            """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, doc.getFilename());
            ps.setString(2, doc.getContentType());
            ps.setString(3, doc.getOriginalText());
            ps.setString(4, doc.getChunks());
            ps.setInt(5, doc.getChunkCount());
            ps.setLong(6, doc.getId());
            ps.executeUpdate();
            System.out.println("[DocumentRepository] 更新文档 id=" + doc.getId() + ", chunks=" + doc.getChunkCount());
        } catch (SQLException e) {
            throw new RuntimeException("更新文档失败", e);
        }
    }

    public List<String> getAllChunks() {
        List<DocumentRecord> docs = findAll();
        List<String> allChunks = new ArrayList<>();
        for (DocumentRecord doc : docs) {
            try {
                List<String> chunks = objectMapper.readValue(doc.getChunks(), new TypeReference<List<String>>() {});
                allChunks.addAll(chunks);
            } catch (JsonProcessingException e) {
                System.out.println("[DocumentRepository] 解析 chunks 失败 for doc id=" + doc.getId() + ": " + e.getMessage());
            }
        }
        return allChunks;
    }

    private DocumentRecord toRecord(ResultSet rs) throws SQLException {
        DocumentRecord doc = new DocumentRecord();
        doc.setId(rs.getLong("id"));
        doc.setFilename(rs.getString("filename"));
        doc.setContentType(rs.getString("content_type"));
        doc.setOriginalText(rs.getString("original_text"));
        doc.setChunks(rs.getString("chunks"));
        doc.setChunkCount(rs.getInt("chunk_count"));
        String uploadTime = rs.getString("upload_time");
        if (uploadTime != null) {
            doc.setUploadTime(LocalDateTime.parse(uploadTime));
        }
        return doc;
    }
}