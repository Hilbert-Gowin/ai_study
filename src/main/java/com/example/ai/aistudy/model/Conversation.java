package com.example.ai.aistudy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {
    private String id;
    private String title;          // 会话标题（取第一条用户消息的前20字符）
    private List<Message> messages = new ArrayList<>();
    private long createdAt;
    private long updatedAt;
}
