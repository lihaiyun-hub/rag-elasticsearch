package com.spring.ai.app.rag.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.ai.app.rag.model.KnowledgeRecord;
import com.spring.ai.app.rag.model.UserContext;
import com.spring.ai.app.rag.security.PromptInjectionFilter;
import com.spring.ai.app.rag.security.ResponseSecurityMonitor;
import com.spring.ai.app.rag.security.SecurityAuditLogger;
import com.spring.ai.app.rag.utils.DocumentParserUtils;
import com.spring.ai.app.rag.services.RetrievalService.RetrievalResult;
import com.spring.ai.app.rag.services.IntentExtractionService.ExtractionResult;
import com.spring.ai.app.rag.tools.ConsumerLoanTools;
import com.spring.ai.app.rag.transformer.ContextualRewriteQueryTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 客户支持助手服务 V2
 * 简化版本：直接集成RAG业务逻辑，避免过深的引用层次
 *
 * @author LHY
 * @date 2025-01-20
 */
@Service("customerSupportAssistantV2")
public class CustomerSupportAssistantV2 {

    private static final Logger logger = LoggerFactory.getLogger(CustomerSupportAssistantV2.class);

    private final RetrievalService retrievalService;
    private final LLMService llmService;
    private final IntentExtractionService intentExtractionService;
    private final PromptInjectionFilter promptInjectionFilter;
    private final ResponseSecurityMonitor responseSecurityMonitor;
    private final SecurityAuditLogger auditLogger;
    private final ConsumerLoanTools consumerLoanTools;
    private final ObjectMapper objectMapper;

    @Value("${app.security.input-check.enabled:true}")
    private boolean inputCheckEnabled;

    @Value("${app.security.output-check.enabled:true}")
    private boolean outputCheckEnabled;

    public CustomerSupportAssistantV2(RetrievalService retrievalService,
                                      LLMService llmService,
                                      IntentExtractionService intentExtractionService,
                                      PromptInjectionFilter promptInjectionFilter,
                                      ResponseSecurityMonitor responseSecurityMonitor,
                                      SecurityAuditLogger auditLogger,
                                      ConsumerLoanTools consumerLoanTools,
                                      ObjectMapper objectMapper) {
        this.retrievalService = retrievalService;
        this.llmService = llmService;
        this.intentExtractionService = intentExtractionService;
        this.promptInjectionFilter = promptInjectionFilter;
        this.responseSecurityMonitor = responseSecurityMonitor;
        this.auditLogger = auditLogger;
        this.consumerLoanTools = consumerLoanTools;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理用户聊天请求
     *
     * @param chatId             会话ID
     * @param userMessageContent 用户消息内容
     * @param userContext        用户上下文
     * @return 助手回复
     */
    public String chat(String chatId, String userMessageContent, UserContext userContext) {
        String userId = userContext != null ? userContext.getUserName() : chatId;

        logger.debug("开始处理聊天请求 - chatId: {}, userId: {}", chatId, userId);

        try {
            // 2. 清理用户输入
            String sanitizedInput = promptInjectionFilter.sanitizeInput(userMessageContent);
            
            // 3. 检查清理后的输入是否为空
            if (sanitizedInput == null || sanitizedInput.trim().isEmpty()) {
                logger.warn("清理后的输入为空 - chatId: {}, userId: {}", chatId, userId);
                return "请输入您的问题，我将为您提供帮助。";
            }
            
            // 4. 设置当前线程的chatId，供QueryTransformer使用
            ContextualRewriteQueryTransformer.setCurrentChatId(chatId);
            try {
                // 5. 执行RAG处理
                String response = processRAG(sanitizedInput, chatId, userContext);
                return response;

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
     * 执行RAG处理 - 简化版本
     */
    private String processRAG(String query, String chatId, UserContext userContext) {
        try {
            // 1. 执行检索
            RetrievalResult retrievalResult = retrievalService.retrieve(query, chatId);

            // 2. 检索失败或无结果时直接返回兜底策略
            if (!retrievalResult.isSuccess() || retrievalResult.getDocuments().isEmpty()) {
                return "抱歉，系统暂时无法处理您的请求，请稍后再试。";
            }
            // 3. 解析最佳匹配的知识库记录
            Document bestDoc = retrievalResult.getDocuments().get(0);
            KnowledgeRecord record;

            try {
                record = DocumentParserUtils.parseDocumentToKnowledgeRecord(bestDoc);
            } catch (Exception e) {
                logger.debug("文档解析失败，使用传统RAG - chatId: {}", chatId, e);
                return "抱歉，系统暂时无法处理您的请求，请稍后再试。";
            }
            // 4. 根据处理方式进行路由
            if (record.isDirectAnswer()) {
                return record.getAnswer();
            }
            ExtractionResult extractionResult = intentExtractionService.extractIntent(query, record, chatId, userContext);
            String jsonOutput = extractionResult.getJsonOutput();
            
            // 解析JSON并根据操作类型进行路由
            return routeByJsonOperation(jsonOutput, userContext, chatId);


        } catch (Exception e) {
            logger.error("RAG处理失败 - query: {}, chatId: {}", query, chatId, e);
            return "抱歉，系统暂时无法处理您的请求，请稍后再试。";
        }
    }




    /**
     * 执行输入安全检查
     */
    private String performInputSecurityCheck(String userMessageContent, String chatId, String userId) {
        try {
            PromptInjectionFilter.DetectionResult injectionResult =
                    promptInjectionFilter.detectInjection(userMessageContent);

            if (injectionResult.isMalicious()) {
                logger.warn("检测到恶意输入 - chatId: {}, userId: {}, riskScore: {}, reason: {}",
                        chatId, userId, injectionResult.getRiskScore(), injectionResult.getReason());

                // 记录攻击行为
                auditLogger.logInputCheck(chatId, userId, false,
                        injectionResult.getRiskScore(), injectionResult.getReason());

                return "检测到异常请求格式，请使用正常的贷款咨询语言重新提问。";
            }
        } catch (Exception e) {
            logger.error("输入安全检查异常 - chatId: {}, userId: {}", chatId, userId, e);
        }

        return null; // 检查通过
    }



    /**
     * 根据JSON操作类型进行路由
     */
    private String routeByJsonOperation(String jsonOutput, UserContext userContext, String chatId) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonOutput);
            String operation = jsonNode.path("operation").asText();
            JsonNode parameters = jsonNode.path("parameters");

            logger.info("JSON路由 - 操作: {}, chatId: {}", operation, chatId);

            switch (operation) {
                case "generate_plan":
                    return handleGeneratePlan(parameters, userContext, chatId);
                case "modify_plan":
                    return handleModifyPlan(parameters, userContext, chatId);
                case "clarification":
                    return handleClarify(parameters, userContext, chatId);
                default:
                    logger.warn("未知操作类型: {} - chatId: {}", operation, chatId);
                    return "抱歉，我无法理解您的请求，请重新描述您的需求。";
            }
        } catch (Exception e) {
            logger.error("JSON解析失败 - jsonOutput: {}, chatId: {}", jsonOutput, chatId, e);
            return "抱歉，系统处理您的请求时出现问题，请稍后再试。";
        }
    }

    /**
     * 处理生成借款方案
     */
    private String handleGeneratePlan(JsonNode parameters, UserContext userContext, String chatId) {
        try {
            // 提取参数
            double amount = parameters.path("amount").asDouble(0);
            int installments = parameters.path("installments").asInt(0);
            String purpose = parameters.path("purpose").asText("");

            // 调用工具方法，从userContext获取userName作为userId
            String userId = userContext != null ? userContext.getUserName() : "default_user";
            return consumerLoanTools.generateLoanOffers(userId, userContext, amount, installments, purpose);
        } catch (Exception e) {
            logger.error("生成借款方案失败 - chatId: {}", chatId, e);
            return "抱歉，生成借款方案时出现问题，请稍后再试。";
        }
    }

    /**
     * 处理修改借款方案
     */
    private String handleModifyPlan(JsonNode parameters, UserContext userContext, String chatId) {
        try {
            // 提取参数
            double amount = parameters.path("amount").asDouble(0);
            int installments = parameters.path("installments").asInt(0);
            String purpose = parameters.path("purpose").asText("");

            // 验证必要参数
            if (amount <= 0 && installments <= 0 && purpose.isEmpty()) {
                return handleClarify(parameters, userContext, chatId);
            }

            // 调用工具方法生成新方案（修改实际上是重新生成），从userContext获取userName作为userId
            String userId = userContext != null ? userContext.getUserName() : "default_user";
            return consumerLoanTools.generateLoanOffers(userId, userContext, amount, installments, purpose);
        } catch (Exception e) {
            logger.error("修改借款方案失败 - chatId: {}", chatId, e);
            return "抱歉，修改借款方案时出现问题，请稍后再试。";
        }
    }

    /**
     * 处理澄清请求
     */
    private String handleClarify(JsonNode parameters, UserContext userContext, String chatId) {
        try {
            StringBuilder clarifyMessage = new StringBuilder("为了更好地为您服务，请提供以下信息：\n\n");

            // 检查需要澄清的参数（值为"true"表示需要澄清该字段）
            String amountStr = parameters.path("amount").asText("").trim();
            String installmentsStr = parameters.path("installments").asText("").trim();
            String purposeStr = parameters.path("purpose").asText("").trim();
            if ("true".equals(amountStr)) {
                clarifyMessage.append("您想借多少钱\n");
            }
            if ("true".equals(installmentsStr)) {
                clarifyMessage.append("• 您希望分多少期还款？（如12期、24期等）\n");
            }
            if ("true".equals(purposeStr)) {
                clarifyMessage.append("• 您的借款用途是什么？（如消费、装修、教育等）\n");
            }
            return clarifyMessage.toString();
        } catch (Exception e) {
            logger.error("处理澄清请求失败 - chatId: {}", chatId, e);
            return "抱歉，请提供您的借款金额、期数和用途，我将为您生成合适的借款方案。";
        }
    }
}