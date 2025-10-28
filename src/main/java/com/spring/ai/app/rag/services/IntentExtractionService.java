package com.spring.ai.app.rag.services;

import com.spring.ai.app.rag.model.KnowledgeRecord;
import com.spring.ai.app.rag.model.UserContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 意图识别和参数提取服务
 * 基于few-shot学习进行意图识别和参数提取
 * 
 * @author AI Assistant
 * @date 2025-10-23
 */
@Service
public class IntentExtractionService {
    
    private static final Logger logger = LoggerFactory.getLogger(IntentExtractionService.class);
    
    private final ChatClient chatClient;
    private final Resource intentExtractionPromptResource;
    private String intentExtractionTemplate;
    
    @Value("${app.intent-extraction.max-retries:3}")
    private int maxRetries;
    
    @Value("${app.intent-extraction.timeout:30000}")
    private long timeoutMs;
    
    public IntentExtractionService(ChatClient.Builder chatClientBuilder, @Qualifier("intentExtractionPrompt") Resource intentExtractionPrompt) {
        this.chatClient = chatClientBuilder.build();
        this.intentExtractionPromptResource = intentExtractionPrompt;
    }
    
    @PostConstruct
    private void initializeTemplate() {
        this.intentExtractionTemplate = loadIntentExtractionTemplate();
    }
    
    /**
     * 从外部文件加载意图提取提示词模板
     */
    private String loadIntentExtractionTemplate() {
        try {
            return intentExtractionPromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("加载意图提取提示词模板失败", e);
            // 提供一个简化的备用模板
            return """
                你是一个专业的意图识别和参数提取助手。
                
                示例查询: {example_query}
                思考过程: {example_cot}
                意图: {example_intent}
                输出结果: {example_output}
                
                用户查询: {user_query}
                
                请按照示例进行分析并输出JSON格式结果。
                """;
        }
    }
    

    
    /**
     * 提取用户查询的意图和参数
     * 
     * @param userQuery 用户查询
     * @param knowledgeRecord 匹配的知识库记录（作为few-shot示例）
     * @param chatId 会话ID
     * @param userContext 用户上下文
     * @return 提取结果
     */
    public ExtractionResult extractIntent(String userQuery, KnowledgeRecord knowledgeRecord, 
                                        String chatId, UserContext userContext) {
        logger.debug("开始意图提取 - userQuery: {}, intent: {}", userQuery, knowledgeRecord.getIntent());
        
        try {
            // 构建few-shot提示词
            String prompt = buildFewShotPrompt(userQuery, knowledgeRecord);
            logger.debug("构建的提示词: {}", prompt);
            
            // 调用LLM进行意图提取
            String response = callLLMWithRetry(prompt, chatId);
            
            // 解析和验证响应
            String jsonOutput = extractJsonFromResponse(response);
            
            if (jsonOutput == null || jsonOutput.trim().isEmpty()) {
                logger.warn("LLM响应中未找到有效的JSON输出 - response: {}", response);
                return ExtractionResult.builder()
                        .userQuery(userQuery)
                        .knowledgeRecord(knowledgeRecord)
                        .rawResponse(response)
                        .jsonOutput("{\"操作\":\"unknown\",\"参数列表\":{}}")
                        .success(false)
                        .errorMessage("未能提取有效的JSON输出")
                        .build();
            }
            
            logger.debug("意图提取成功 - jsonOutput: {}", jsonOutput);
            
            return ExtractionResult.builder()
                    .userQuery(userQuery)
                    .knowledgeRecord(knowledgeRecord)
                    .rawResponse(response)
                    .jsonOutput(jsonOutput)
                    .success(true)
                    .build();
                    
        } catch (Exception e) {
            logger.error("意图提取失败 - userQuery: {}", userQuery, e);
            return ExtractionResult.builder()
                    .userQuery(userQuery)
                    .knowledgeRecord(knowledgeRecord)
                    .success(false)
                    .errorMessage("意图提取失败: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 构建few-shot提示词
     */
    private String buildFewShotPrompt(String userQuery, KnowledgeRecord knowledgeRecord) {
        PromptTemplate promptTemplate = new PromptTemplate(intentExtractionTemplate);
        
        Map<String, Object> variables = Map.of(
                "example_query", knowledgeRecord.getQuery(),
                "example_cot", knowledgeRecord.getCotThinking(),
                "example_intent", knowledgeRecord.getIntent(),
                "example_output", knowledgeRecord.getAnswer(),
                "user_query", userQuery
        );
        
        Prompt prompt = promptTemplate.create(variables);
        return prompt.getContents();
    }
    
    /**
     * 带重试机制的LLM调用
     */
    private String callLLMWithRetry(String prompt, String chatId) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.debug("LLM调用尝试 {} / {}", attempt, maxRetries);
                
                String response = chatClient
                        .prompt(prompt)
                        .call()
                        .content();
                
                if (response != null && !response.trim().isEmpty()) {
                    logger.debug("LLM调用成功 - attempt: {}", attempt);
                    return response;
                }
                
                logger.warn("LLM返回空响应 - attempt: {}", attempt);
                
            } catch (Exception e) {
                lastException = e;
                logger.warn("LLM调用失败 - attempt: {} / {}", attempt, maxRetries, e);
                
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1000 * attempt); // 递增延迟
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("线程被中断", ie);
                    }
                }
            }
        }
        
        throw new RuntimeException("LLM调用失败，已重试 " + maxRetries + " 次", lastException);
    }
    
    /**
     * 从LLM响应中提取JSON内容
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }
        
        // 查找JSON代码块
        String jsonBlockStart = "```json";
        String jsonBlockEnd = "```";
        
        int startIndex = response.indexOf(jsonBlockStart);
        if (startIndex != -1) {
            startIndex += jsonBlockStart.length();
            int endIndex = response.indexOf(jsonBlockEnd, startIndex);
            if (endIndex != -1) {
                return response.substring(startIndex, endIndex).trim();
            }
        }
        
        // 如果没有找到代码块，尝试查找JSON对象
        int jsonStart = response.indexOf("{");
        int jsonEnd = response.lastIndexOf("}");
        
        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd + 1).trim();
        }
        
        // 如果都没找到，返回原始响应
        logger.warn("无法从响应中提取JSON - response: {}", response);
        return response.trim();
    }
    
    /**
     * 意图提取结果
     */
    public static class ExtractionResult {
        private final String userQuery;
        private final KnowledgeRecord knowledgeRecord;
        private final String rawResponse;
        private final String jsonOutput;
        private final boolean success;
        private final String errorMessage;
        private final long processingTimeMs;
        
        private ExtractionResult(Builder builder) {
            this.userQuery = builder.userQuery;
            this.knowledgeRecord = builder.knowledgeRecord;
            this.rawResponse = builder.rawResponse;
            this.jsonOutput = builder.jsonOutput;
            this.success = builder.success;
            this.errorMessage = builder.errorMessage;
            this.processingTimeMs = builder.processingTimeMs;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public String getUserQuery() { return userQuery; }
        public KnowledgeRecord getKnowledgeRecord() { return knowledgeRecord; }
        public String getRawResponse() { return rawResponse; }
        public String getJsonOutput() { return jsonOutput; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        
        public static class Builder {
            private String userQuery;
            private KnowledgeRecord knowledgeRecord;
            private String rawResponse;
            private String jsonOutput;
            private boolean success;
            private String errorMessage;
            private long processingTimeMs;
            
            public Builder userQuery(String userQuery) { this.userQuery = userQuery; return this; }
            public Builder knowledgeRecord(KnowledgeRecord knowledgeRecord) { this.knowledgeRecord = knowledgeRecord; return this; }
            public Builder rawResponse(String rawResponse) { this.rawResponse = rawResponse; return this; }
            public Builder jsonOutput(String jsonOutput) { this.jsonOutput = jsonOutput; return this; }
            public Builder success(boolean success) { this.success = success; return this; }
            public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
            public Builder processingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; return this; }
            
            public ExtractionResult build() {
                return new ExtractionResult(this);
            }
        }
    }
}