package com.spring.ai.app.rag.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.ai.app.rag.config.MemoryConfig;
import com.spring.ai.app.rag.flow.*;
import com.spring.ai.app.rag.security.PromptInjectionFilter;
import com.spring.ai.app.rag.security.ResponseSecurityMonitor;
import com.spring.ai.app.rag.security.SecurityAuditLogger;
import com.spring.ai.app.rag.tools.ChangePlanTools;
import com.spring.ai.app.rag.tools.TimeTools;
import com.spring.ai.app.rag.tools.WeatherTools;
import com.spring.ai.app.rag.model.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor.TOP_K;
import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;


@Service
public class CustomerSupportAssistant {

    private static final Logger logger = LoggerFactory.getLogger(CustomerSupportAssistant.class);
    private ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final MemoryConfig memoryConfig;
    private final PromptInjectionFilter promptInjectionFilter;
    private final ResponseSecurityMonitor responseSecurityMonitor;
    private final SecurityAuditLogger auditLogger;
    private final IntentExtractor intentExtractor;
    private final IntentRouter intentRouter;
    private final ConsumerCreditService consumerCreditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CustomerSupportAssistant(Resource systemPromptResource,
                                    ChatClient.Builder modelBuilder,
                                    RetrievalAugmentationAdvisor retrievalAugmentationAdvisor,
                                    PromptChatMemoryAdvisor promptChatMemoryAdvisor,
                                    ObjectProvider<ToolCallbackProvider> toolCallbackProvider,
                                    ChatMemory chatMemory,
                                    MemoryConfig memoryConfig,
                                    PromptInjectionFilter promptInjectionFilter,
                                    ResponseSecurityMonitor responseSecurityMonitor,
                                    SecurityAuditLogger auditLogger,
                                    IntentExtractor intentExtractor,
                                    IntentRouter intentRouter,
                                    ConsumerCreditService consumerCreditService) throws IOException {
        var builder = modelBuilder
                .defaultSystem(systemPromptResource)
                .defaultTools( new ChangePlanTools(),new WeatherTools(),new TimeTools())
                .defaultAdvisors(retrievalAugmentationAdvisor, promptChatMemoryAdvisor)
                // 当 ToolCallbackProvider 不存在时，跳过工具回调注册，保证应用可启动
                ;
        var provider = toolCallbackProvider != null ? toolCallbackProvider.getIfAvailable() : null;
        if (provider != null) {
            builder = builder.defaultToolCallbacks(provider.getToolCallbacks());
        }
        builder = builder.defaultOptions(ToolCallingChatOptions.builder()
                        .internalToolExecutionEnabled(true)
                        .build());

        this.chatClient = builder.build();
        this.chatMemory = chatMemory;
        this.memoryConfig = memoryConfig;
        this.promptInjectionFilter = promptInjectionFilter;
        this.responseSecurityMonitor = responseSecurityMonitor;
        this.auditLogger = auditLogger;
        this.intentExtractor = intentExtractor;
        this.intentRouter = intentRouter;
        this.consumerCreditService = consumerCreditService;
        // @formatter:on
    }

    /**
     * 将历史记录和当前查询格式化成特殊格式，以便QueryTransformer可以解析
     */
    private String formatQueryWithHistory(List<Message> history, String currentQuery) {
        StringBuilder formatted = new StringBuilder();

        // 添加历史记录分隔符
        formatted.append("===HISTORY_START===\n");
        for (Message message : history) {
            formatted.append(message.getMessageType()).append(":")
                    .append(message.getText()).append("\n");
        }
        formatted.append("===HISTORY_END===\n");

        // 添加当前查询
        formatted.append("===CURRENT_QUERY===\n");
        formatted.append(currentQuery);

        return formatted.toString();
    }



    public String chat(String chatId, String userMessageContent, UserContext userContext) {
        // 获取用户ID（从userContext或chatId）
        String userId = userContext != null ? userContext.getUserName() : chatId;

        try {
            // ==== 零侵入式多流程路由 ====
            var intent = intentExtractor.extract(userMessageContent);

            // ==== 消费贷授信流程 ====
            if ("CONSUMER_LOAN".equals(intent.intent())) {
                Boolean authorized = userContext != null ? userContext.getAuthorized() : null;
                if (Boolean.TRUE.equals(authorized)) {
                    // 已授信，直接推送借款方案卡片（前端兼容：offer 以 JSON 字符串提供）
                    Map<String,String> payload = new HashMap<>();
                    payload.put("action","confirm_loan");
                    payload.put("label","确认借款");
                    payload.put("change_action","change_plan");
                    payload.put("change_label","更换方案");
                    // 将借款方案以 JSON 字符串嵌入 payload.offer，前端解析后用于渲染
                    payload.put("offer", "{\"amount\":48000,\"annualRate\":14.4,\"termMonths\":12,\"repayMode\":\"每期等本\",\"firstPayment\":4614.40,\"totalInterest\":3832.00,\"bankName\":\"网商银行\",\"bankTail\":\"5386\",\"lender\":\"福建海峡银行\",\"discountText\":\"查看优惠\",\"purpose\":\"消费购物\"}");
                    Card loanCard = new Card("consumer_loan_offers", "为您推荐以下消费贷方案", "多款额度可选，随借随还","intent",payload);
                    ChatResponse resp = new ChatResponse("card_list",List.of(loanCard),"CREDIT_DONE",
                            "");
                    return objectMapper.writeValueAsString(resp);
                } else {
                    // 未授信或未提供授权状态：统一通过状态机启动流程，避免出现“未知步骤”
                    consumerCreditService.initIfAbsent(chatId);
                    IntentResult ir = new IntentResult("CONSUMER_CREDIT", 0.9, Map.of(), null);
                    ChatResponse resp = intentRouter.route(chatId, ir);
                    return objectMapper.writeValueAsString(resp);
                }
            }

            // 2. 输入安全检查
            PromptInjectionFilter.DetectionResult injectionResult =
                    promptInjectionFilter.detectInjection(userMessageContent);

            if (injectionResult.isMalicious()) {
                logger.warn("检测到恶意输入 - chatId: {}, userId: {}, riskScore: {}, reason: {}",
                        chatId, userId, injectionResult.getRiskScore(), injectionResult.getReason());

                // 记录攻击行为
                auditLogger.logInputCheck(chatId, userId, false, injectionResult.getRiskScore(), injectionResult.getReason());

                return "检测到异常请求格式，请使用正常的贷款咨询语言重新提问。";
            }

            // 3. 清理用户输入
            String sanitizedInput = promptInjectionFilter.sanitizeInput(userMessageContent);

            // 4. 记录正常请求
            auditLogger.logInputCheck(chatId, userId, true, 0.0, "安全检查通过");

            // 5. 获取对话历史记录
            List<Message> history = chatMemory.get(chatId);

            logger.debug("Original query: {}", userMessageContent);
            logger.debug("Sanitized query: {}", sanitizedInput);
            logger.debug("History messages count: {}", history.size());
            if (!history.isEmpty() && logger.isDebugEnabled()) {
                logger.debug("Last message from history: {}", history.get(history.size() - 1).getText());
            }

            // 6. 将历史记录和当前查询拼接成特殊格式，以便QueryTransformer可以获取历史
            String enhancedQuery = formatQueryWithHistory(history, sanitizedInput);
            logger.debug("Query formatted with history markers for transformer processing");

            // 7. 构建system prompt参数
            var systemParams = buildSystemParams(userContext);

            // 打印最终发送到模型的参数（简化日志，避免冗余的历史标记）
            logger.info("最终发送到AI模型的参数:");
            logger.info("System参数: {}", systemParams);
            logger.info("当前日期: {}", LocalDate.now().toString());
            logger.info("当前时间: {}", java.time.LocalDateTime.now().toString());
            logger.info("用户查询: {} (历史记录已包含)", sanitizedInput);
            logger.info("Advisor参数: chatId={}, topK={}", chatId, memoryConfig.getTopK());

            // 8. 调用chatClient获取响应
            String response = this.chatClient.prompt()
                    .system(s -> systemParams.forEach(s::param))
                    .user(enhancedQuery)
                    .advisors(a -> a.param(CONVERSATION_ID, chatId).param(TOP_K, memoryConfig.getTopK()))
                    .call()
                    .content();

            // 9. 响应安全检查
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

        } catch (Exception e) {
            logger.error("处理聊天请求时发生异常 - chatId: {}, userId: {}", chatId, userId, e);
            auditLogger.logSystemException(chatId, userId, e.getClass().getSimpleName(), e.getMessage());
            return "系统处理异常，请稍后再试。";
        }
    }

    private Map<String, Object> buildSystemParams(UserContext userContext) {
        Map<String, Object> params = new HashMap<>();

        // 添加用户上下文信息 - 使用系统提示模板所需的变量名
        if (userContext != null) {
            params.put("user_name", userContext.getUserName());
            params.put("available_credit", userContext.getAvailableCredit());
            params.put("current_loan_plan", userContext.getCurrentLoanPlan());
            params.put("recent_repayment_status", userContext.getRecentRepaymentStatus());
            params.put("max_loan_amount", userContext.getMaxLoanAmount());
        } else {
            // 使用默认值，确保模板变量都能被替换
            params.put("user_name", "尊敬的客户");
            params.put("available_credit", 10000.0);
            params.put("current_loan_plan", "无");
            params.put("recent_repayment_status", "正常");
            params.put("max_loan_amount", 50000.0);
        }

        // 添加时间工具
        params.put("current_time", java.time.LocalDateTime.now().toString());

        return params;
    }
}
