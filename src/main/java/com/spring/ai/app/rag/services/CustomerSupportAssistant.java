package com.spring.ai.app.rag.services;

import com.spring.ai.app.rag.config.MemoryConfig;
import com.spring.ai.app.rag.model.UserContext;
import com.spring.ai.app.rag.security.PromptInjectionFilter;
import com.spring.ai.app.rag.security.ResponseSecurityMonitor;
import com.spring.ai.app.rag.security.SecurityAuditLogger;
import com.spring.ai.app.rag.tools.ConsumerLoanTools;
import com.spring.ai.app.rag.transformer.ContextualRewriteQueryTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor.TOP_K;
import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;


@Service
public class CustomerSupportAssistant {

    private static final Logger logger = LoggerFactory.getLogger(CustomerSupportAssistant.class);
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final MemoryConfig memoryConfig;
    private final PromptInjectionFilter promptInjectionFilter;
    private final ResponseSecurityMonitor responseSecurityMonitor;
    private final SecurityAuditLogger auditLogger;

    public CustomerSupportAssistant(Resource systemPromptResource,
                                     ChatClient.Builder modelBuilder,
                                     RetrievalAugmentationAdvisor retrievalAugmentationAdvisor,
                                     PromptChatMemoryAdvisor promptChatMemoryAdvisor,
                                     ChatMemory chatMemory,
                                     MemoryConfig memoryConfig,
                                     PromptInjectionFilter promptInjectionFilter,
                                     ResponseSecurityMonitor responseSecurityMonitor,
                                     SecurityAuditLogger auditLogger,
                                     ConsumerLoanTools consumerLoanTools) {
        var builder = modelBuilder
                .defaultSystem(systemPromptResource)
                .defaultAdvisors(retrievalAugmentationAdvisor, promptChatMemoryAdvisor);

        builder = builder.defaultTools(
                consumerLoanTools);

        builder = builder.defaultOptions(ToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(true)
                .build());

        this.chatClient = builder.build();
        this.chatMemory = chatMemory;
        this.memoryConfig = memoryConfig;
        this.promptInjectionFilter = promptInjectionFilter;
        this.responseSecurityMonitor = responseSecurityMonitor;
        this.auditLogger = auditLogger;
    }






    public String chat(String chatId, String userMessageContent, UserContext userContext) {
        // 获取用户ID（从userContext或chatId）
        String userId = userContext != null ? userContext.getUserName() : chatId;

        try {
            // 1. 输入安全检查
            PromptInjectionFilter.DetectionResult injectionResult =
                    promptInjectionFilter.detectInjection(userMessageContent);

            if (injectionResult.isMalicious()) {
                logger.warn("检测到恶意输入 - chatId: {}, userId: {}, riskScore: {}, reason: {}",
                        chatId, userId, injectionResult.getRiskScore(), injectionResult.getReason());

                // 记录攻击行为
                auditLogger.logInputCheck(chatId, userId, false, injectionResult.getRiskScore(), injectionResult.getReason());

                return "检测到异常请求格式，请使用正常的贷款咨询语言重新提问。";
            }

            // 2. 清理用户输入
            String sanitizedInput = promptInjectionFilter.sanitizeInput(userMessageContent);

            // 3. 记录正常请求
            auditLogger.logInputCheck(chatId, userId, true, 0.0, "安全检查通过");

            // 4. 获取对话历史记录
            List<Message> history = chatMemory.get(chatId);

            if (!history.isEmpty() && logger.isDebugEnabled()) {
                logger.debug("Last message from history: {}", history.get(history.size() - 1).getText());
            }

            // 5. 构建system prompt参数
            var systemParams = buildSystemParams(userContext);

            // 6. 设置当前线程的chatId，供QueryTransformer使用
            ContextualRewriteQueryTransformer.setCurrentChatId(chatId);
            
            try {
                // 7. 调用chatClient获取响应（现在包含意图识别和RAG检索）
                // 关键：直接使用sanitizedInput，让QueryTransformer从ChatMemory获取历史记录
                String response = this.chatClient.prompt()
                        .system(s -> systemParams.forEach(s::param))
                        .user(sanitizedInput)  // 使用纯净的用户输入
                        .advisors(a -> a.param(CONVERSATION_ID, chatId)
                                       .param(TOP_K, memoryConfig.getTopK()))
                        .call()
                        .content();
                
                return processResponse(response, chatId, userId, userMessageContent);
            } finally {
                // 8. 清除ThreadLocal，避免内存泄漏
                ContextualRewriteQueryTransformer.clearCurrentChatId();
            }
        } catch (Exception e) {
            // 确保在异常情况下也清除ThreadLocal
            ContextualRewriteQueryTransformer.clearCurrentChatId();
            logger.error("聊天处理异常 - chatId: {}, userId: {}", chatId, userId, e);
            return "抱歉，系统暂时无法处理您的请求，请稍后再试。";
        }
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

    private Map<String, Object> buildSystemParams(UserContext userContext) {
        Map<String, Object> params = new HashMap<>();

        // 添加用户上下文信息 - 使用系统提示模板所需的变量名
        if (userContext != null) {
            params.put("user_name", userContext.getUserName());
            params.put("available_credit", userContext.getAvailableCredit());

            params.put("recent_repayment_status", userContext.getRecentRepaymentStatus());
            // 授信状态字符串：null -> 未提供；true -> 已授信；false -> 未授信
            String authStatus = userContext.getAuthorized() == null
                    ? "未提供"
                    : (userContext.getAuthorized() ? "已授信" : "未授信");
            params.put("authorization_status", authStatus);
            // 动态分期与用途范围
            String termOptionsStr;
            if (userContext.getTermOptions() != null && !userContext.getTermOptions().isEmpty()) {
                termOptionsStr = userContext.getTermOptions().stream()
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining("/")) + "期";
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
            // 移除 max_loan_amount
        } else {
            // 使用默认值，确保模板变量都能被替换
            params.put("user_name", "尊敬的客户");
            params.put("available_credit", 10000.0);

            params.put("recent_repayment_status", "正常");
            params.put("authorization_status", "未提供");
            params.put("term_options", "3/6/9/12/18/24期");
            params.put("loan_purposes", "日常消费、教育培训、医疗健康、家庭装修、旅游出行、数码家电、其他");
            // 移除 max_loan_amount 默认值
        }

        // 添加时间工具
        params.put("current_time", java.time.LocalDateTime.now().toString());

        return params;
    }


}
