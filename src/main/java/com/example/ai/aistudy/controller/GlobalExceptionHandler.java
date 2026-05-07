package com.example.ai.aistudy.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Map<String, String> handleMaxSize(MaxUploadSizeExceededException e) {
        return Map.of("message", "文件过大，请上传小于 " + (e.getMaxUploadSize() / 1024 / 1024) + "MB 的文件");
    }

    @ExceptionHandler(Exception.class)
    public Map<String, String> handleGeneral(Exception e) {
        return Map.of("message", "服务器错误: " + e.getClass().getSimpleName() + " - " + e.getMessage());
    }
}