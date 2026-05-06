package com.example.ai.aistudy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Scores {
    private int accuracy;
    private int completeness;
    private int consistency;
    private int conciseness;
    private double total;
}
