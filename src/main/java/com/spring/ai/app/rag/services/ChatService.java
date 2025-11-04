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
        List<Message> history = chatMemory.get(chatId);
        try {
            // 1. 输入安全检查和清理
            String sanitizedInput = promptInjectionFilter.sanitizeInput(userMessage);
            if (sanitizedInput == null || sanitizedInput.trim().isEmpty()) {
                return "请输入您的问题，我将为您提供帮助。";
            }
            // 2. 数字输入校验与转换（抽取为独立方法）
            NumericInputCheckResult numericCheck = applyNumericInputRules(chatId, history, sanitizedInput);
            if (numericCheck.getEarlyResponse() != null) {
                // 已在方法内记录对话历史，这里直接返回早期提示
                return numericCheck.getEarlyResponse();
            }
            sanitizedInput = numericCheck.getProcessedInput();

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
     * 数字输入校验与转换的总控方法（抽取自 chat() 中的逻辑片段）。
     *
     * 处理顺序与规则说明：
     * 1) 首条纯数字：若该会话的首个用户消息的第一句仅包含数字，则在该句前自动加上“借”前缀。
     *    - 例如："1000" → "借1000"；"1000。我要分期" → "借1000。我要分期"。
     *    - 第一句的边界以常见标点或换行符判定：。！？.!?\n\r。
     * 2) 非首条且纯数字的早期提示：
     *    - 若输入为纯数字且在 16..99 之间，返回提示语，要求用户重新输入需求，避免无效金额或分期数。
     *    - 在触发提示时，本方法会统一写入对话历史（用户输入与助手提示），并通过 earlyResponse 返回。
     * 3) 非首条且纯数字的转换：
     *    - 1..15 → 转为“分{n}期”；>100 → 前缀为“借{n}”。
     *    - 其余情况（含 0、<=15 非正、=100、非数字）保持原样。
     *
     * 返回值约定：
     * - 若需要直接提示用户（早退），则 earlyResponse 非空，processedInput 为触发提示时的用户输入版本；
     * - 若不需要早退，则 earlyResponse 为空，processedInput 为校验/转换后的输入。
     *
     * 设计说明：
     * - 抽取为独立方法，便于集中维护数字相关的业务规则，减少 chat() 的分支复杂度；
     * - 本方法内部负责在早退场景下统一记录聊天历史，调用方只需根据 earlyResponse 是否为空决定是否返回即可。
     */
    private NumericInputCheckResult applyNumericInputRules(String chatId, List<Message> history, String input) {
        try {
            // Step 1: 首条纯数字 → 第一句自动加“借”前缀
            String updated = maybeAutoPrefixBorrowIfFirstNumericSentence(history, input);

            // Step 2: 非首条且纯数字 → 16..99 返回早期提示（并写入聊天历史）
            String earlyResponse = maybeEarlyResponseForNonFirstNumericInput(history, updated);
            if (earlyResponse != null) {
                chatMemory.add(chatId, Message.builder().type(Message.Type.USER).content(updated).build());
                chatMemory.add(chatId, Message.builder().type(Message.Type.ASSISTANT).content(earlyResponse).build());
                return new NumericInputCheckResult(updated, earlyResponse);
            }

            // Step 3: 非首条且纯数字 → 1..15 转“分{n}期”；>100 转“借{n}”
            updated = maybeTransformNonFirstNumericInput(history, updated);
            return new NumericInputCheckResult(updated, null);
        } catch (Exception e) {
            // 任何异常均不影响主流程，返回原始输入以保证稳健性
            logger.debug("数字输入校验与转换失败，回退到原始输入: {}", e.toString());
            return new NumericInputCheckResult(input, null);
        }
    }

    /**
     * 抽取方法的返回封装：
     * - processedInput：校验/转换后的输入（或原始输入，取决于规则触发）
     * - earlyResponse：当需要直接给出提示并早退时的助手回复；否则为 null
     */
    private static class NumericInputCheckResult {
        private final String processedInput;
        private final String earlyResponse;

        private NumericInputCheckResult(String processedInput, String earlyResponse) {
            this.processedInput = processedInput;
            this.earlyResponse = earlyResponse;
        }

        public String getProcessedInput() {
            return processedInput;
        }

        public String getEarlyResponse() {
            return earlyResponse;
        }
    }


    /**
     * 若该会话是用户的首条输入，且第一句仅为数字，则在前面加“借”。
     */
    private String maybeAutoPrefixBorrowIfFirstNumericSentence(List<Message> history, String input) {
        try {
            boolean firstUserInput = true;
            if (history != null) {
                for (Message m : history) {
                    if (m != null && m.getType() == Message.Type.USER) {
                        firstUserInput = false;
                        break;
                    }
                }
            }
            if (!firstUserInput) {
                return input;
            }

            String s = input;
            int boundary = -1;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?' || c == '\n' || c == '\r') {
                    boundary = i;
                    break;
                }
            }
            String first = boundary == -1 ? s : s.substring(0, boundary);
            String normalized = first.replaceAll("[\\s\\p{Punct}，。！？、；：—-]", "");
            if (!normalized.isEmpty() && normalized.matches("\\d+")) {
                String updated = "借" + first + (boundary == -1 ? "" : s.substring(boundary));
                logger.info("前缀修正：首条用户输入为纯数字，已自动加前缀 -> {}", updated);
                return updated;
            }
        } catch (Exception e) {
            logger.debug("前缀修正跳过，原因: {}", e.toString());
        }
        return input;
    }

    /**
     * 非首条用户输入且仅为数字：
     * - 16..99：返回提示，要求重新输入
     * - 其他：不在此处处理（交由转换方法或原样）
     */
    private String maybeEarlyResponseForNonFirstNumericInput(List<Message> history, String input) {
        try {
            boolean hasPriorUser = false;
            if (history != null) {
                for (Message m : history) {
                    if (m != null && m.getType() == Message.Type.USER) {
                        hasPriorUser = true;
                        break;
                    }
                }
            }
            if (!hasPriorUser) {
                return null;
            }

            String trimmed = input == null ? "" : input.trim();
            if (!trimmed.matches("\\d+")) {
                return null; // 不是“只包含数字”
            }
            int n = Integer.parseInt(trimmed);
            if (n > 15 && n < 100) {
                String msg = "借款金额不得小于100，可选分期数为[]，请重新输入需求。";
                logger.info("数值输入规则触发：n={}，提示用户重新输入", n);
                return msg;
            }
        } catch (Exception e) {
            logger.debug("非首条数值输入提示跳过，原因: {}", e.toString());
        }
        return null;
    }

    /**
     * 非首条用户输入且仅为数字：
     * - 1..15：转换为“分{n}期”
     * - >100：前缀“借{n}”
     * - 其他（含0、<=15且非正、=100、非数字）：原样返回
     */
    private String maybeTransformNonFirstNumericInput(List<Message> history, String input) {
        try {
            boolean hasPriorUser = false;
            if (history != null) {
                for (Message m : history) {
                    if (m != null && m.getType() == Message.Type.USER) {
                        hasPriorUser = true;
                        break;
                    }
                }
            }
            if (!hasPriorUser) {
                return input;
            }

            String trimmed = input == null ? "" : input.trim();
            if (!trimmed.matches("\\d+")) {
                return input; // 不是“只包含数字”
            }
            int n = Integer.parseInt(trimmed);
            if (n > 0 && n <= 15) {
                String updated = "分" + n + "期";
                logger.info("数值输入转换：n={} -> {}", n, updated);
                return updated;
            }
            if (n > 100) {
                String updated = "借" + n;
                logger.info("数值输入转换：n={} -> {}", n, updated);
                return updated;
            }
        } catch (Exception e) {
            logger.debug("非首条数值输入转换跳过，原因: {}", e.toString());
        }
        return input;
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

