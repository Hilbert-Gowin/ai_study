package com.example.ai.aistudy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rag.rerank")
public class RerankConfig {

    private boolean enabled = true;
    private int initialTopK = 10;
    private int finalTopK = 3;
    private double vectorWeight = 0.4;
    private double keywordWeight = 0.3;
    private double exactMatchWeight = 0.2;
    private double lengthWeight = 0.1;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getInitialTopK() {
        return initialTopK;
    }

    public void setInitialTopK(int initialTopK) {
        this.initialTopK = initialTopK;
    }

    public int getFinalTopK() {
        return finalTopK;
    }

    public void setFinalTopK(int finalTopK) {
        this.finalTopK = finalTopK;
    }

    public double getVectorWeight() {
        return vectorWeight;
    }

    public void setVectorWeight(double vectorWeight) {
        this.vectorWeight = vectorWeight;
    }

    public double getKeywordWeight() {
        return keywordWeight;
    }

    public void setKeywordWeight(double keywordWeight) {
        this.keywordWeight = keywordWeight;
    }

    public double getExactMatchWeight() {
        return exactMatchWeight;
    }

    public void setExactMatchWeight(double exactMatchWeight) {
        this.exactMatchWeight = exactMatchWeight;
    }

    public double getLengthWeight() {
        return lengthWeight;
    }

    public void setLengthWeight(double lengthWeight) {
        this.lengthWeight = lengthWeight;
    }
}
