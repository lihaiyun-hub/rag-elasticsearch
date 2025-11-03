package com.spring.ai.app.rag.vectorstore;

import java.util.Map;

/**
 * 搜索结果类
 */
public record SearchResult(String text, Map<String, Object> metadata) {

    public Double getScore() {
        Object score = metadata.get("score");
        return score instanceof Number ? ((Number) score).doubleValue() : null;
    }

    public String getId() {
        return (String) metadata.get("id");
    }
}