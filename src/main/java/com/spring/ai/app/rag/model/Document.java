package com.spring.ai.app.rag.model;

import java.util.Map;

/**
 * 文档模型
 */
public record Document(String content, Map<String, Object> metadata) {

    /**
     * 获取文档内容
     */
    @Override
    public String content() {
        return content;
    }

    /**
     * 获取文档元数据
     */
    @Override
    public Map<String, Object> metadata() {
        return metadata;
    }

    /**
     * 获取文档内容（兼容旧方法）
     */
    public String getText() {
        return content();
    }

    /**
     * 创建文档构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 文档构建器
     */
    public static class Builder {
        private String content;
        private Map<String, Object> metadata;

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Document build() {
            return new Document(content, metadata);
        }
    }
}