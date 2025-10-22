package com.spring.ai.app.rag.intent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.ai.app.rag.flow.IntentResult;
import com.spring.ai.app.rag.model.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一的意图识别和槽位抽取服务
 * 实现高阶方案D：合并意图抽槽节点
 * 
 * 功能特性：
 * 1. 统一处理意图识别和槽位抽取
 * 2. 上下文感知的意图理解
 * 3. 多轮对话状态管理
 * 4. 结构化输出格式
 * 
 * @author LHY
 * @date 2025-01-XX
 */
@Service
public class IntentSlotExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(IntentSlotExtractionService.class);
    
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final PromptTemplate intentSlotPromptTemplate;
    private final ObjectMapper objectMapper;

    public IntentSlotExtractionService(ChatClient.Builder chatClientBuilder, 
                                     ChatMemory chatMemory) {
        this.chatClient = chatClientBuilder.build();
        this.chatMemory = chatMemory;
        this.objectMapper = new ObjectMapper();
        
        // 加载统一的意图槽位抽取提示模板
        Resource promptResource = new DefaultResourceLoader()
                .getResource("classpath:prompts/intent-slot-extraction-prompt.st");
        this.intentSlotPromptTemplate = PromptTemplate.builder()
                .resource(promptResource)
                .build();
    }

    /**
     * 统一执行意图识别和槽位抽取
     * 
     * @param chatId 会话ID
     * @param userInput 用户输入
     * @param userContext 用户上下文
     * @return 意图识别和槽位抽取结果
     */
    public IntentSlotExtractionResult extractIntentAndSlots(String chatId, 
                                                           String userInput, 
                                                           UserContext userContext) {
        try {
            // 1. 获取对话历史
            String conversationHistory = buildConversationHistory(chatId);
            
            // 2. 构建上下文信息
            String contextInfo = buildContextInfo(userContext);
            
            // 3. 构建提示参数
            Map<String, Object> promptParams = Map.of(
                "user_input", userInput,
                "conversation_history", conversationHistory,
                "context_info", contextInfo,
                "current_time", java.time.LocalDateTime.now().toString()
            );
            
            // 4. 调用LLM进行统一的意图槽位抽取
            String prompt = intentSlotPromptTemplate.render(promptParams);
            String response = chatClient.prompt(prompt).call().content();
            
            // 5. 解析结构化响应
            return parseIntentSlotResponse(response, userInput);
            
        } catch (Exception e) {
            logger.error("意图槽位抽取失败 - chatId: {}, userInput: {}", chatId, userInput, e);
            // 返回默认结果
            return createDefaultResult(userInput);
        }
    }

    /**
     * 构建对话历史上下文
     */
    private String buildConversationHistory(String chatId) {
        if (chatId == null || chatMemory == null) {
            return "";
        }
        
        List<Message> messages = chatMemory.get(chatId);
        if (messages.isEmpty()) {
            return "";
        }
        
        StringBuilder historyBuilder = new StringBuilder();
        // 只取最近5轮对话，避免上下文过长
        int startIndex = Math.max(0, messages.size() - 10); // 5轮对话=10条消息
        
        for (int i = startIndex; i < messages.size(); i++) {
            Message message = messages.get(i);
            historyBuilder.append(message.getMessageType().name())
                    .append(": ")
                    .append(message.getText())
                    .append("\n");
        }
        
        return historyBuilder.toString();
    }

    /**
     * 构建用户上下文信息
     */
    private String buildContextInfo(UserContext userContext) {
        if (userContext == null) {
            return "用户上下文：未提供";
        }
        
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("用户上下文：\n");
        
        if (userContext.getUserName() != null) {
            contextBuilder.append("- 用户姓名：").append(userContext.getUserName()).append("\n");
        }
        
        if (userContext.getAvailableCredit() != null) {
            contextBuilder.append("- 可用额度：").append(userContext.getAvailableCredit()).append("元\n");
        }
        
        if (userContext.getAuthorized() != null) {
            String authStatus = userContext.getAuthorized() ? "已授信" : "未授信";
            contextBuilder.append("- 授信状态：").append(authStatus).append("\n");
        }
        
        if (userContext.getRecentRepaymentStatus() != null) {
            contextBuilder.append("- 还款状态：").append(userContext.getRecentRepaymentStatus()).append("\n");
        }
        
        return contextBuilder.toString();
    }

    /**
     * 解析LLM返回的结构化响应
     */
    private IntentSlotExtractionResult parseIntentSlotResponse(String response, String originalInput) {
        try {
            // 尝试解析JSON响应
            JsonNode jsonNode = objectMapper.readTree(response);
            
            String intent = jsonNode.path("intent").asText("OTHER");
            double confidence = jsonNode.path("confidence").asDouble(0.5);
            
            // 解析槽位
            Map<String, String> slots = new HashMap<>();
            JsonNode slotsNode = jsonNode.path("slots");
            if (slotsNode.isObject()) {
                slotsNode.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue().asText();
                    if (value != null && !value.trim().isEmpty()) {
                        slots.put(key, value);
                    }
                });
            }
            
            // 解析授信状态
            Boolean consumerLoanAuthorized = null;
            if (jsonNode.has("consumer_loan_authorized")) {
                consumerLoanAuthorized = jsonNode.path("consumer_loan_authorized").asBoolean();
            }
            
            // 解析推理过程
            String reasoning = jsonNode.path("reasoning").asText("");
            
            // 解析建议的下一步动作
            String suggestedAction = jsonNode.path("suggested_action").asText("");
            
            return new IntentSlotExtractionResult(
                new IntentResult(intent, confidence, slots, consumerLoanAuthorized),
                reasoning,
                suggestedAction,
                true
            );
            
        } catch (JsonProcessingException e) {
            logger.warn("解析意图槽位响应失败，使用文本解析 - response: {}", response, e);
            return parseTextResponse(response, originalInput);
        }
    }

    /**
     * 文本解析备用方案
     */
    private IntentSlotExtractionResult parseTextResponse(String response, String originalInput) {
        // 简单的文本解析逻辑
        String intent = "OTHER";
        double confidence = 0.5;
        Map<String, String> slots = new HashMap<>();
        
        // 基于关键词判断意图
        String lowerResponse = response.toLowerCase();
        if (lowerResponse.contains("inquiry") || lowerResponse.contains("咨询")) {
            intent = "INQUIRY";
            confidence = 0.7;
        } else if (lowerResponse.contains("operation") || lowerResponse.contains("操作")) {
            intent = "OPERATION";
            confidence = 0.7;
        } else if (lowerResponse.contains("clarification") || lowerResponse.contains("澄清")) {
            intent = "CLARIFICATION";
            confidence = 0.7;
        }
        
        return new IntentSlotExtractionResult(
            new IntentResult(intent, confidence, slots, null),
            "文本解析结果",
            "",
            false
        );
    }

    /**
     * 创建默认结果（异常情况下使用）
     */
    private IntentSlotExtractionResult createDefaultResult(String originalInput) {
        return new IntentSlotExtractionResult(
            new IntentResult("OTHER", 0.1, new HashMap<>(), null),
            "系统异常，使用默认结果",
            "",
            false
        );
    }

    /**
     * 意图槽位抽取结果
     */
    public static class IntentSlotExtractionResult {
        private final IntentResult intentResult;
        private final String reasoning;
        private final String suggestedAction;
        private final boolean isStructuredResponse;

        public IntentSlotExtractionResult(IntentResult intentResult, 
                                        String reasoning, 
                                        String suggestedAction, 
                                        boolean isStructuredResponse) {
            this.intentResult = intentResult;
            this.reasoning = reasoning;
            this.suggestedAction = suggestedAction;
            this.isStructuredResponse = isStructuredResponse;
        }

        public IntentResult getIntentResult() {
            return intentResult;
        }

        public String getReasoning() {
            return reasoning;
        }

        public String getSuggestedAction() {
            return suggestedAction;
        }

        public boolean isStructuredResponse() {
            return isStructuredResponse;
        }
    }
}