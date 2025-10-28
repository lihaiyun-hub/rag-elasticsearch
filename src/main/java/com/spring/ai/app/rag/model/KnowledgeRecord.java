package com.spring.ai.app.rag.model;

/**
 * 知识库记录模型
 * 用于存储查询、处理方式、答案和思考过程
 * 
 * @author AI Assistant
 * @date 2025-10-23
 */
public class KnowledgeRecord {
    
    /**
     * 处理方式枚举
     */
    public enum ProcessingType {
        DIRECT_ANSWER("直接回答"),
        INTENT_ROUTING("意图路由");
        
        private final String description;
        
        ProcessingType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    private final String query;
    private final ProcessingType processingType;
    private final String answer;
    private final String cotThinking;  // Chain of Thought 思考过程
    private final String intent;       // 意图（仅当processingType为INTENT_ROUTING时有效）
    
    private KnowledgeRecord(Builder builder) {
        this.query = builder.query;
        this.processingType = builder.processingType;
        this.answer = builder.answer;
        this.cotThinking = builder.cotThinking;
        this.intent = builder.intent;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public String getQuery() {
        return query;
    }
    
    public ProcessingType getProcessingType() {
        return processingType;
    }
    
    public String getAnswer() {
        return answer;
    }
    
    public String getCotThinking() {
        return cotThinking;
    }
    
    public String getIntent() {
        return intent;
    }
    
    public boolean isDirectAnswer() {
        return processingType == ProcessingType.DIRECT_ANSWER;
    }
    
    public boolean isIntentRouting() {
        return processingType == ProcessingType.INTENT_ROUTING;
    }
    
    public static class Builder {
        private String query;
        private ProcessingType processingType;
        private String answer;
        private String cotThinking;
        private String intent;
        
        public Builder query(String query) {
            this.query = query;
            return this;
        }
        
        public Builder processingType(ProcessingType processingType) {
            this.processingType = processingType;
            return this;
        }
        
        public Builder answer(String answer) {
            this.answer = answer;
            return this;
        }
        
        public Builder cotThinking(String cotThinking) {
            this.cotThinking = cotThinking;
            return this;
        }
        
        public Builder intent(String intent) {
            this.intent = intent;
            return this;
        }
        
        public KnowledgeRecord build() {
            return new KnowledgeRecord(this);
        }
    }
    
    @Override
    public String toString() {
        return "KnowledgeRecord{" +
                "query='" + query + '\'' +
                ", processingType=" + processingType +
                ", answer='" + answer + '\'' +
                ", cotThinking='" + cotThinking + '\'' +
                ", intent='" + intent + '\'' +
                '}';
    }
}