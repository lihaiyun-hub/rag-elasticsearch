package com.spring.ai.app.rag.services;

import com.spring.ai.app.rag.model.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * 独立的大模型服务
 * 负责大模型调用逻辑，与检索逻辑解耦
 * 
 * @author LHY
 * @date 2025-01-20
 */
@Service
public class LLMService {

    private static final Logger logger = LoggerFactory.getLogger(LLMService.class);
    
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final PromptChatMemoryAdvisor promptChatMemoryAdvisor;

    public LLMService(@Qualifier("systemPrompt") Resource systemPromptResource,
                     ChatClient.Builder modelBuilder,
                     ChatMemory chatMemory,
                     PromptChatMemoryAdvisor promptChatMemoryAdvisor) {
        
        // 创建不包含RAG Advisor的ChatClient
        this.chatClient = modelBuilder
                .defaultSystem(systemPromptResource)
                .defaultAdvisors(promptChatMemoryAdvisor)
                .defaultOptions(ToolCallingChatOptions.builder()
                        .internalToolExecutionEnabled(true)
                        .build())
                .build();
                
        this.chatMemory = chatMemory;
        this.promptChatMemoryAdvisor = promptChatMemoryAdvisor;
    }

    /**
     * 基于检索到的文档生成回答
     * 
     * @param query 用户查询
     * @param documents 检索到的相关文档
     * @param chatId 会话ID
     * @param userContext 用户上下文
     * @return 生成的回答
     */
    public LLMResponse generateResponse(String query, List<Document> documents, 
                                      String chatId, UserContext userContext) {
        logger.debug("开始生成回答 - query: {}, documents: {}, chatId: {}", 
                    query, documents.size(), chatId);
        
        try {
            // 1. 构建上下文信息
            String context = buildContext(documents);
            
            // 2. 构建系统参数
            Map<String, Object> systemParams = buildSystemParams(userContext);
            
            // 3. 构建增强的用户提示
            String enhancedPrompt = buildEnhancedPrompt(query, context);
            
            // 4. 调用大模型
            String response = chatClient.prompt()
                    .system(s -> systemParams.forEach(s::param))
                    .user(enhancedPrompt)
                    .advisors(a -> a.param(CONVERSATION_ID, chatId))
                    .call()
                    .content();
            
            logger.debug("回答生成完成 - chatId: {}", chatId);
            
            return LLMResponse.builder()
                    .query(query)
                    .context(context)
                    .response(response)
                    .success(true)
                    .build();
                    
        } catch (Exception e) {
            logger.error("回答生成失败 - query: {}, chatId: {}", query, chatId, e);
            return LLMResponse.builder()
                    .query(query)
                    .context("")
                    .response("抱歉，系统暂时无法处理您的请求，请稍后再试。")
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 直接调用大模型，不使用检索上下文
     * 用于非RAG场景
     */
    public LLMResponse generateDirectResponse(String query, String chatId, UserContext userContext) {
        logger.debug("开始直接生成回答 - query: {}, chatId: {}", query, chatId);
        
        try {
            Map<String, Object> systemParams = buildSystemParams(userContext);
            
            String response = chatClient.prompt()
                    .system(s -> systemParams.forEach(s::param))
                    .user(query)
                    .advisors(a -> a.param(CONVERSATION_ID, chatId))
                    .call()
                    .content();
            
            return LLMResponse.builder()
                    .query(query)
                    .context("")
                    .response(response)
                    .success(true)
                    .build();
                    
        } catch (Exception e) {
            logger.error("直接回答生成失败 - query: {}, chatId: {}", query, chatId, e);
            return LLMResponse.builder()
                    .query(query)
                    .context("")
                    .response("抱歉，系统暂时无法处理您的请求，请稍后再试。")
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 获取会话历史
     */
    public List<Message> getChatHistory(String chatId) {
        return chatMemory.get(chatId);
    }

    /**
     * 构建文档上下文
     */
    private String buildContext(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "";
        }
        
        return documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 构建增强的用户提示
     */
    private String buildEnhancedPrompt(String query, String context) {
        if (context == null || context.trim().isEmpty()) {
            return query;
        }
        
        return String.format("""
                基于以下相关信息回答用户问题：
                
                相关信息：
                %s
                
                用户问题：%s
                
                请基于提供的相关信息给出准确、有用的回答。如果相关信息不足以回答问题，请说明并提供一般性建议。
                """, context, query);
    }

    /**
     * 构建系统参数
     */
    private Map<String, Object> buildSystemParams(UserContext userContext) {
        Map<String, Object> params = new HashMap<>();

        if (userContext != null) {
            params.put("user_name", userContext.getUserName());
            params.put("available_credit", userContext.getAvailableCredit());
            params.put("recent_repayment_status", userContext.getRecentRepaymentStatus());
            
            String authStatus = userContext.getAuthorized() == null
                    ? "未提供"
                    : (userContext.getAuthorized() ? "已授信" : "未授信");
            params.put("authorization_status", authStatus);
            
            String termOptionsStr;
            if (userContext.getTermOptions() != null && !userContext.getTermOptions().isEmpty()) {
                termOptionsStr = userContext.getTermOptions().stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining("/")) + "期";
            } else {
                termOptionsStr = "3/6/9/12/18/24期";
            }
            params.put("term_options", termOptionsStr);

            String loanPurposesStr;
            if (userContext.getLoanPurposes() != null && !userContext.getLoanPurposes().isEmpty()) {
                loanPurposesStr = String.join("、", userContext.getLoanPurposes());
            } else {
                loanPurposesStr = "日常消费、教育培训、医疗健康、家庭装修、旅游出行、数码家电、其他";
            }
            params.put("loan_purposes", loanPurposesStr);
        } else {
            // 默认值
            params.put("user_name", "尊敬的客户");
            params.put("available_credit", 10000.0);
            params.put("recent_repayment_status", "正常");
            params.put("authorization_status", "未提供");
            params.put("term_options", "3/6/9/12/18/24期");
            params.put("loan_purposes", "日常消费、教育培训、医疗健康、家庭装修、旅游出行、数码家电、其他");
        }

        params.put("current_time", java.time.LocalDateTime.now().toString());
        return params;
    }

    /**
     * LLM响应封装类
     */
    public static class LLMResponse {
        private final String query;
        private final String context;
        private final String response;
        private final boolean success;
        private final String errorMessage;

        private LLMResponse(Builder builder) {
            this.query = builder.query;
            this.context = builder.context;
            this.response = builder.response;
            this.success = builder.success;
            this.errorMessage = builder.errorMessage;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public String getQuery() { return query; }
        public String getContext() { return context; }
        public String getResponse() { return response; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }

        public static class Builder {
            private String query;
            private String context;
            private String response;
            private boolean success;
            private String errorMessage;

            public Builder query(String query) {
                this.query = query;
                return this;
            }

            public Builder context(String context) {
                this.context = context;
                return this;
            }

            public Builder response(String response) {
                this.response = response;
                return this;
            }

            public Builder success(boolean success) {
                this.success = success;
                return this;
            }

            public Builder errorMessage(String errorMessage) {
                this.errorMessage = errorMessage;
                return this;
            }

            public LLMResponse build() {
                return new LLMResponse(this);
            }
        }
    }
}