package com.spring.ai.app.rag.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.ai.app.rag.chat.ChatClient;
import com.spring.ai.app.rag.chat.ChatMemory;
import com.spring.ai.app.rag.model.*;
import com.spring.ai.app.rag.reranker.RerankPostProcessor;
import com.spring.ai.app.rag.retriever.ConfigurableDocumentRetriever;
import com.spring.ai.app.rag.security.PromptInjectionFilter;
import com.spring.ai.app.rag.tools.ConsumerLoanTools;
import com.spring.ai.app.rag.utils.DocumentParserUtils;
import com.spring.ai.app.rag.utils.JsonExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;


/**
 * 聊天服务
 * 实现基于Elasticsearch的RAG对话功能
 */
@Service
public class ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final ConfigurableDocumentRetriever documentRetriever;
    private final ChatClient chatClient;
    private final PromptInjectionFilter promptInjectionFilter;

    private final ChatMemory chatMemory;   // ← 统一聊天历史存储
    private final RerankPostProcessor rerankPostProcessor;
    private final ConsumerLoanTools consumerLoanTools;
    private final ObjectMapper objectMapper;

    public ChatService(
            ConfigurableDocumentRetriever documentRetriever,
            ChatClient chatClient,
            PromptInjectionFilter promptInjectionFilter,

            ChatMemory chatMemory,
            RerankPostProcessor rerankPostProcessor,
            ConsumerLoanTools consumerLoanTools,
            ObjectMapper objectMapper) {
        this.documentRetriever = documentRetriever;
        this.chatClient = chatClient;
        this.promptInjectionFilter = promptInjectionFilter;

        this.chatMemory = chatMemory;
        this.rerankPostProcessor = rerankPostProcessor;
        this.consumerLoanTools = consumerLoanTools;
        this.objectMapper = objectMapper;

    }

    /**
     * 处理用户聊天请求
     *
     * @param chatId      会话ID
     * @param userMessage 用户消息
     * @param userContext 用户上下文
     * @return 助手回复
     */
    public String chat(String chatId, String userMessage, UserContext userContext) {
        String userId = userContext != null ? userContext.getUserName() : chatId;

        try {
            // 1. 输入安全检查和清理
            String sanitizedInput = promptInjectionFilter.sanitizeInput(userMessage);
            if (sanitizedInput == null || sanitizedInput.trim().isEmpty()) {
                return "请输入您的问题，我将为您提供帮助。";
            }

            // 2. 取出本会话历史（可选：拼到 prompt 里）
            List<Message> history = chatMemory.get(chatId);

            // 3. 检索：将“最新query + 历史对话”交给检索层处理（包含增强查询与批量检索）
            Query retrievalQuery = new Query(sanitizedInput, Map.of("history", history), chatId);
            List<Document> documents = documentRetriever.retrieve(retrievalQuery);
            if (documents.isEmpty()) {
                return "抱歉，未找到相关内容，请换个说法试试。";
            }
            // 传递 history 给重排序，使其可选择使用增强查询进行多路重排序融合
//            documents = rerankPostProcessor.process(retrievalQuery, documents);

            for (int i = 0; i < documents.size(); i++) {
                com.spring.ai.app.rag.model.Document d = documents.get(i);
                String text = d != null ? d.getText() : "";
                String snippet = text == null ? "" : (text.length() > 200 ? text.substring(0, 200) + "..." : text);
                logger.info("文档#{} | 内容片段=\"{}\" | metadata={}", i + 1, snippet, d != null ? d.metadata() : Map.of());
            }
            KnowledgeRecord record = DocumentParserUtils.parseDocumentToKnowledgeRecord(documents.get(0));

            String finalAnswer;
            switch (record.getProcessingType()) {
                case DIRECT_ANSWER -> {
                    finalAnswer = record.getAnswer();
                    logger.info("【直接回答】query={} -> answer={}", sanitizedInput, finalAnswer);
                }
                case INTENT_ROUTING -> {
                    logger.info("【意图路由】query={} -> 开始提取意图", sanitizedInput);
                    try {
                        String raw = chatClient.prompt()
                                .system(s -> {
                                    s.param("example_query", record.getQuery());
                                    s.param("example_cot", record.getCotThinking());
                                    s.param("example_output", record.getAnswer());
                                })
                                .user(sanitizedInput)
                                .call()
                                .content();
                        String jsonOutput = JsonExtractor.extractJsonFromResponse(raw);
                        logger.info("大模型返回结果：{}", jsonOutput);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> root = objectMapper.readValue(jsonOutput, Map.class);
                        String intent = String.valueOf(root.getOrDefault("intent", "")).trim();
                        @SuppressWarnings("unchecked")
                        Map<String, Object> params = (Map<String, Object>) root.getOrDefault("parameters", Map.of());
                        // 授权状态解析：优先使用 workFlowFlag（01=未授信，02=已授信），否则回退到 userContext.authorized
                        boolean authorized = resolveAuthorized(userContext);

                        switch (intent) {
                            case "loan_application", "apply_loan" ->
                                // 借款：已授信→生成方案；未授信→开启授信
                                    finalAnswer = authorized
                                            ? consumerLoanTools.generateLoanOffers(userId, userContext, params)
                                            : consumerLoanTools.handleApplyCreditLimit(userId, userContext);
                            case "query_credit_limit", "credit_apply", "apply_limit" ->
                                // 查询/申请额度：已授信→返回额度话术；未授信→开启授信
                                    finalAnswer = authorized
                                            ? consumerLoanTools.queryCreditLimit(userId, userContext)
                                            : consumerLoanTools.handleApplyCreditLimit(userId, userContext);
                            default ->
                                // 未识别的操作：直接回传模型JSON输出
                                    finalAnswer = jsonOutput;
                        }
                    } catch (Exception ex) {
                        logger.warn("意图路由分支处理失败，使用降级回复: {}", ex.toString());
                        // 优先使用知识库中的示例回答作为降级结果
                        finalAnswer = record.getAnswer();
                    }

                }
                default -> finalAnswer = "未知的处理类型";
            }
            // 4. 持久化本次对话（用户问 & 助手答）
            chatMemory.add(chatId, Message.builder().type(Message.Type.USER).content(sanitizedInput).build());
            chatMemory.add(chatId, Message.builder().type(Message.Type.ASSISTANT).content(finalAnswer).build());

            return finalAnswer;
        } catch (Exception e) {
            logger.error("聊天处理异常 - chatId: {}, userId: {}", chatId, userId, e);
            return "抱歉，系统暂时无法处理您的请求，请稍后再试。";
        }
    }


    /**
     * 解析授权状态：
     * - workFlowFlag="01" → 未授信(false)
     * - workFlowFlag="02" → 已授信(true)
     * - 其他/空 → 回退到 userContext.getAuthorized()
     */
    private boolean resolveAuthorized(UserContext userContext) {
        String wf = userContext != null ? userContext.getWorkFlowFlag() : null;
        if (wf != null) {
            wf = wf.trim();
            if ("01".equals(wf)) return false;
            if ("02".equals(wf)) return true;
        }
        Boolean authorizedObj = userContext != null ? userContext.getAuthorized() : null;
        return Boolean.TRUE.equals(authorizedObj);
    }

}
