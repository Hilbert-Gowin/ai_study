package com.example.ai.aistudy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FaqAddResponse {
    private boolean success;
    private String message;
    private List<ChunkInfo> chunks;
}
