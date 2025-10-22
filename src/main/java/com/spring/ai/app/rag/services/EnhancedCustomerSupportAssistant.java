package com.spring.ai.app.rag.services;

import com.spring.ai.app.rag.config.MemoryConfig;
import com.spring.ai.app.rag.intent.IntentSlotExtractionService;
import com.spring.ai.app.rag.model.UserContext;
import com.spring.ai.app.rag.retrieval.EnhancedPreRagService;
import com.spring.ai.app.rag.security.PromptInjectionFilter;
import com.spring.ai.app.rag.security.ResponseSecurityMonitor;
import com.spring.ai.app.rag.security.SecurityAuditLogger;
import com.spring.ai.app.rag.tools.ConsumerLoanTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.rag.Query;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * 增强版客服助手
 * 实现高阶方案D：合并意图抽槽节点 + 升级前置RAG召回能力
 * 
 * 核心改进：
 * 1. 统一意图识别和槽位抽取
 * 2. 增强前置RAG召回（多路召回、重排序、上下文增强）
 * 3. 意图感知的检索优化
 * 4. 智能路由和响应生成
 * 
 * @author LHY
 * @date 2025-01-XX
 */
@Service
public class EnhancedCustomerSupportAssistant {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedCustomerSupportAssistant.class);

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final MemoryConfig memoryConfig;
    private final IntentSlotExtractionService intentSlotService;
    private final EnhancedPreRagService enhancedPreRagService;
    private final PromptInjectionFilter promptInjectionFilter;
    private final ResponseSecurityMonitor responseSecurityMonitor;
    private final SecurityAuditLogger auditLogger;

    @Value("${spring.ai.rag.enhanced.enabled:true}")
    private boolean enhancedRagEnabled;

    @Value("${spring.ai.rag.enhanced.intent-slot.enabled:true}")
    private boolean intentSlotEnabled;

    public EnhancedCustomerSupportAssistant(Resource systemPromptResource,
                                          ChatClient.Builder modelBuilder,
                                          PromptChatMemoryAdvisor promptChatMemoryAdvisor,
                                          ChatMemory chatMemory,
                                          MemoryConfig memoryConfig,
                                          IntentSlotExtractionService intentSlotService,
                                          EnhancedPreRagService enhancedPreRagService,
                                          PromptInjectionFilter promptInjectionFilter,
                                          ResponseSecurityMonitor responseSecurityMonitor,
                                          SecurityAuditLogger auditLogger,
                                          ConsumerLoanTools consumerLoanTools) {
        
        // 构建ChatClient，不使用传统的RetrievalAugmentationAdvisor
        // 因为我们使用自定义的增强RAG流程
        var builder = modelBuilder
                .defaultSystem(systemPromptResource)
                .defaultAdvisors(promptChatMemoryAdvisor);

        builder = builder.defaultTools(consumerLoanTools);

        builder = builder.defaultOptions(ToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(true)
                .build());

        this.chatClient = builder.build();
        this.chatMemory = chatMemory;
        this.memoryConfig = memoryConfig;
        this.intentSlotService = intentSlotService;
        this.enhancedPreRagService = enhancedPreRagService;
        this.promptInjectionFilter = promptInjectionFilter;
        this.responseSecurityMonitor = responseSecurityMonitor;
        this.auditLogger = auditLogger;
    }

    /**
     * 增强版聊天处理
     * 实现高阶方案D的完整流程
     */
    public String chat(String chatId, String userMessageContent, UserContext userContext) {
        String userId = userContext != null ? userContext.getUserName() : chatId;

        try {
            // 1. 输入安全检查
            PromptInjectionFilter.DetectionResult injectionResult =
                    promptInjectionFilter.detectInjection(userMessageContent);

            if (injectionResult.isMalicious()) {
                logger.warn("检测到恶意输入 - chatId: {}, userId: {}, riskScore: {}, reason: {}",
                        chatId, userId, injectionResult.getRiskScore(), injectionResult.getReason());
                auditLogger.logInputCheck(chatId, userId, false, injectionResult.getRiskScore(), injectionResult.getReason());
                return "检测到异常请求格式，请使用正常的贷款咨询语言重新提问。";
            }

            // 2. 清理用户输入
            String sanitizedInput = promptInjectionFilter.sanitizeInput(userMessageContent);
            auditLogger.logInputCheck(chatId, userId, true, 0.0, "安全检查通过");

            // 3. 统一意图识别和槽位抽取（高阶方案D核心特性1）
            IntentSlotExtractionService.IntentSlotExtractionResult intentSlotResult = null;
            if (intentSlotEnabled) {
                intentSlotResult = extractIntentAndSlots(chatId, sanitizedInput, userContext);
                logger.info("意图槽位抽取完成 - chatId: {}, intent: {}, confidence: {}, slots: {}", 
                    chatId, 
                    intentSlotResult.getIntentResult().intent(),
                    intentSlotResult.getIntentResult().confidence(),
                    intentSlotResult.getIntentResult().slots());
            }

            // 4. 增强前置RAG召回（高阶方案D核心特性2）
            EnhancedPreRagService.EnhancedRetrievalResult retrievalResult = null;
            if (enhancedRagEnabled && intentSlotResult != null) {
                Query query = Query.builder().text(sanitizedInput).build();
                retrievalResult = enhancedPreRagService.enhancedRetrieve(query, intentSlotResult, userContext);
                
                logger.info("增强RAG召回完成 - chatId: {}, 多路召回: {}, 去重后: {}, 重排序后: {}, 最终结果: {}", 
                    chatId,
                    retrievalResult.getMultiRecallCount(),
                    retrievalResult.getDeduplicatedCount(), 
                    retrievalResult.getRerankedCount(),
                    retrievalResult.getDocuments().size());
            }

            // 5. 构建增强的系统提示参数
            Map<String, Object> systemParams = buildEnhancedSystemParams(userContext, intentSlotResult, retrievalResult);

            // 6. 生成响应
            String response = generateResponse(chatId, sanitizedInput, systemParams, retrievalResult);

            // 7. 响应处理和安全检查
            return processResponse(response, chatId, userId, userMessageContent);

        } catch (Exception e) {
            logger.error("增强聊天处理异常 - chatId: {}, userId: {}", chatId, userId, e);
            return "抱歉，系统暂时无法处理您的请求，请稍后再试。";
        }
    }

    /**
     * 统一意图识别和槽位抽取
     */
    private IntentSlotExtractionService.IntentSlotExtractionResult extractIntentAndSlots(
            String chatId, String userInput, UserContext userContext) {
        try {
            return intentSlotService.extractIntentAndSlots(chatId, userInput, userContext);
        } catch (Exception e) {
            logger.error("意图槽位抽取失败 - chatId: {}, input: {}", chatId, userInput, e);
            // 返回默认结果
            return new IntentSlotExtractionService.IntentSlotExtractionResult(
                new com.spring.ai.app.rag.flow.IntentResult("OTHER", 0.5, Map.of(), false),
                "意图识别失败，使用默认处理",
                "继续使用RAG检索",
                false // isStructuredResponse
            );
        }
    }

    /**
     * 生成响应
     */
    private String generateResponse(String chatId, 
                                  String userInput, 
                                  Map<String, Object> systemParams,
                                  EnhancedPreRagService.EnhancedRetrievalResult retrievalResult) {
        
        // 构建用户提示，包含检索到的上下文
        String enhancedUserPrompt = buildEnhancedUserPrompt(userInput, retrievalResult);
        
        return chatClient.prompt()
                .system(s -> systemParams.forEach(s::param))
                .user(enhancedUserPrompt)
                .advisors(a -> a.param(CONVERSATION_ID, chatId))
                .call()
                .content();
    }

    /**
     * 构建增强的用户提示
     */
    private String buildEnhancedUserPrompt(String userInput, 
                                         EnhancedPreRagService.EnhancedRetrievalResult retrievalResult) {
        StringBuilder promptBuilder = new StringBuilder();
        
        // 添加检索上下文
        if (retrievalResult != null && !retrievalResult.getDocuments().isEmpty()) {
            promptBuilder.append("相关知识库信息：\n");
            
            List<Document> documents = retrievalResult.getDocuments();
            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                promptBuilder.append(String.format("[文档%d] %s\n", i + 1, doc.getText()));
                
                // 添加增强的元数据信息
                Map<String, Object> metadata = doc.getMetadata();
                if (metadata.containsKey("detected_intent")) {
                    promptBuilder.append(String.format("  - 相关意图: %s\n", metadata.get("detected_intent")));
                }
                if (metadata.containsKey("extracted_slots")) {
                    promptBuilder.append(String.format("  - 相关参数: %s\n", metadata.get("extracted_slots")));
                }
            }
            promptBuilder.append("\n");
        }
        
        // 添加用户查询
        promptBuilder.append("用户问题：").append(userInput);
        
        return promptBuilder.toString();
    }

    /**
     * 构建增强的系统参数
     */
    private Map<String, Object> buildEnhancedSystemParams(UserContext userContext,
                                                         IntentSlotExtractionService.IntentSlotExtractionResult intentSlotResult,
                                                         EnhancedPreRagService.EnhancedRetrievalResult retrievalResult) {
        Map<String, Object> params = new HashMap<>();
        
        // 基础用户上下文参数
        if (userContext != null) {
            params.put("user_name", userContext.getUserName());
            params.put("available_credit", userContext.getAvailableCredit());
            params.put("recent_repayment_status", userContext.getRecentRepaymentStatus());
            
            String authStatus = userContext.getAuthorized() == null
                    ? "未提供"
                    : (userContext.getAuthorized() ? "已授信" : "未授信");
            params.put("authorization_status", authStatus);
            
            // 动态分期与用途范围
            String termOptionsStr = userContext.getTermOptions() != null && !userContext.getTermOptions().isEmpty()
                    ? userContext.getTermOptions().stream().map(String::valueOf).collect(Collectors.joining("/")) + "期"
                    : "3/6/9/12/18/24期";
            params.put("term_options", termOptionsStr);
            
            String loanPurposesStr = userContext.getLoanPurposes() != null && !userContext.getLoanPurposes().isEmpty()
                    ? String.join("、", userContext.getLoanPurposes())
                    : "日常消费、教育培训、医疗健康、家庭装修、旅游出行、数码家电、其他";
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
        
        // 增强参数：意图和槽位信息
        if (intentSlotResult != null) {
            params.put("detected_intent", intentSlotResult.getIntentResult().intent());
            params.put("intent_confidence", intentSlotResult.getIntentResult().confidence());
            params.put("extracted_slots", formatSlots(intentSlotResult.getIntentResult().slots()));
            params.put("authorization_required", intentSlotResult.getIntentResult().consumerLoanAuthorized());
            params.put("extraction_reasoning", intentSlotResult.getReasoning());
            params.put("suggested_action", intentSlotResult.getSuggestedAction());
        }
        
        // 增强参数：检索指标
        if (retrievalResult != null) {
            EnhancedPreRagService.RetrievalMetrics metrics = retrievalResult.getMetrics();
            params.put("retrieval_total_results", metrics.getTotalResults());
            params.put("retrieval_avg_relevance", String.format("%.2f", metrics.getAverageRelevance()));
            params.put("retrieval_coverage", String.format("%.2f", metrics.getCoverage()));
            params.put("retrieval_diversity", String.format("%.2f", metrics.getDiversity()));
            
            // 查询扩展信息
            if (!retrievalResult.getExpandedQueries().isEmpty()) {
                String expandedQueriesStr = retrievalResult.getExpandedQueries().stream()
                    .map(Query::text)
                    .collect(Collectors.joining("; "));
                params.put("expanded_queries", expandedQueriesStr);
            }
        }
        
        // 时间信息
        params.put("current_time", java.time.LocalDateTime.now().toString());
        
        return params;
    }

    /**
     * 格式化槽位信息
     */
    private String formatSlots(Map<String, String> slots) {
        if (slots == null || slots.isEmpty()) {
            return "无";
        }
        
        return slots.entrySet().stream()
            .map(entry -> entry.getKey() + ": " + entry.getValue())
            .collect(Collectors.joining(", "));
    }

    /**
     * 处理响应和安全检查
     */
    private String processResponse(String response, String chatId, String userId, String userMessageContent) {
        // 响应安全检查
        ResponseSecurityMonitor.SecurityCheckResult securityResult =
                responseSecurityMonitor.checkResponse(userMessageContent, response);

        if (!securityResult.isSafe()) {
            logger.error("响应安全检查失败 - chatId: {}, userId: {}, riskScore: {}, reason: {}",
                    chatId, userId, securityResult.getRiskScore(), securityResult.getReason());

            auditLogger.logOutputCheck(chatId, userId, false, securityResult.getRiskScore(), securityResult.getReason());

            return "抱歉，系统检测到异常响应，请稍后再试或联系客服。";
        }

        auditLogger.logOutputCheck(chatId, userId, true, 0.0, "响应安全检查通过");

        return response;
    }
}