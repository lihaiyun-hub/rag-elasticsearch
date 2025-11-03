package com.spring.ai.app.rag.chat;

/**
 * 工具调用聊天选项
 */
public class ToolCallingChatOptions {
    private final boolean internalToolExecutionEnabled;

    private ToolCallingChatOptions(Builder builder) {
        this.internalToolExecutionEnabled = builder.internalToolExecutionEnabled;
    }

    /**
     * 是否启用内部工具执行
     */
    public boolean isInternalToolExecutionEnabled() {
        return internalToolExecutionEnabled;
    }

    /**
     * 创建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 工具调用聊天选项构建器
     */
    public static class Builder {
        private boolean internalToolExecutionEnabled;

        /**
         * 设置是否启用内部工具执行
         */
        public Builder internalToolExecutionEnabled(boolean enabled) {
            this.internalToolExecutionEnabled = enabled;
            return this;
        }

        /**
         * 构建工具调用聊天选项
         */
        public ToolCallingChatOptions build() {
            return new ToolCallingChatOptions(this);
        }
    }
}