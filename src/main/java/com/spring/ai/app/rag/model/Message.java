package com.spring.ai.app.rag.model;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 聊天消息
 */
public class Message {
    /**
     * 消息类型
     */
    public enum Type {
        SYSTEM,    // 系统消息
        USER,      // 用户消息
        ASSISTANT  // 助手消息
    }

    private final Type type;
    private final String content;
    private final Map<String, Object> metadata;
    private final LocalDateTime timestamp;

    private Message(Builder builder) {
        this.type = builder.type;
        this.content = builder.content;
        this.metadata = builder.metadata;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
    }

    public Type getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Type type;
        private String content;
        private Map<String, Object> metadata;
        private LocalDateTime timestamp;

        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Message build() {
            return new Message(this);
        }
    }
}