package com.spring.ai.app.rag.model;

import java.util.Map;

/**
 * 查询模型
 */
public class Query {
    private final String text;
    private final Map<String, Object> metadata;
    private final String chatId;

    public Query(String text, Map<String, Object> metadata) {
        this.text = text;
        this.metadata = metadata;
        this.chatId = null;
    }

    public Query(String text, Map<String, Object> metadata, String chatId) {
        this.text = text;
        this.metadata = metadata;
        this.chatId = chatId;
    }

    /**
     * 获取查询文本
     */
    public String getText() {
        return text;
    }

    /**
     * 获取查询元数据
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * 获取会话ID
     */
    public String getChatId() {
        return chatId;
    }

    /**
     * 创建查询构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 查询构建器
     */
    public static class Builder {
        private String text;
        private Map<String, Object> metadata;
        private String chatId;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder chatId(String chatId) {
            this.chatId = chatId;
            return this;
        }

        public Query build() {
            return new Query(text, metadata, chatId);
        }
    }
}
