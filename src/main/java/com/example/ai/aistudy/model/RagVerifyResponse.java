package com.example.ai.aistudy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 校验响应，包含初筛结果、重排结果和详细评分
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagVerifyResponse {
    private String query;
    private boolean rerankEnabled;
    private List<ChunkSource> coarseResults;
    private List<ChunkSource> finalResults;
    private List<RerankDetail> rerankDetails;
}
