package com.example.ai.aistudy.parser;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Component
public class DocumentParser {

    private final Tika tika = new Tika();

    /**
     * 解析上传的文件，提取纯文本内容
     * 支持：PDF, DOCX, DOC, TXT, HTML, PPTX, PPT, RTF 等
     */
    public String parse(MultipartFile file) throws IOException {
        try (InputStream stream = file.getInputStream()) {
            String text = tika.parseToString(stream);
            return text.trim();
        } catch (TikaException e) {
            throw new IOException("文件解析失败: " + file.getOriginalFilename(), e);
        }
    }

    /**
     * 从 InputStream 解析（供其他场景使用）
     */
    public String parse(InputStream stream, String filename) throws IOException {
        try {
            return tika.parseToString(stream).trim();
        } catch (TikaException e) {
            throw new IOException("解析失败: " + filename, e);
        }
    }

    /**
     * 检测文件类型
     */
    public String detectContentType(MultipartFile file) throws IOException {
        return tika.detect(file.getInputStream());
    }
}