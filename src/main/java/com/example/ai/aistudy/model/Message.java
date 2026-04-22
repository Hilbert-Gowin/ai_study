package com.example.ai.aistudy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private String role;      // "user" 或 "assistant"
    private String content;   // 消息内容
    private long timestamp;   // 时间戳
}
