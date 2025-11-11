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
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
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

    public ChatService(
            ConfigurableDocumentRetriever documentRetriever,
            ChatClient chatClient,
            PromptInjectionFilter promptInjectionFilter,

            ChatMemory chatMemory,
            RerankPostProcessor rerankPostProcessor,
            ConsumerLoanTools consumerLoanTools) {
        this.documentRetriever = documentRetriever;
        this.chatClient = chatClient;
        this.promptInjectionFilter = promptInjectionFilter;

        this.chatMemory = chatMemory;
        this.rerankPostProcessor = rerankPostProcessor;
        this.consumerLoanTools = consumerLoanTools;

    }

    /**
     * 处理用户聊天请求
     *
     * @param userMessage 用户消息
     * @param userContext 用户上下文
     * @return 助手回复
     */
    public ChatVO chat(String userMessage, UserContext userContext) {
        String storageId = buildStorageId(userContext);
        boolean authorized = userContext.getAuthorized();
        Integer messageType = userContext.getMessageType();
        List<Message> history = chatMemory.get(storageId);
        ChatVO chatVO = null;
        try {
            switch (messageType) {
                case 1:
                    // 首次推荐借款方案
                    chatVO = consumerLoanTools.generateLoanOffers(userContext, new HashMap<>());
                    break;
                case 2:
                    // 通用聊天
                    chatVO = chat(userMessage, userContext, storageId, authorized, history);
                    break;
                default:
                    // 流程处理
                    chatVO = processWorkflow(userMessage, userContext, storageId, authorized, history);
                    break;
            }
            return chatVO;


        } catch (Exception e) {
            logger.error("聊天处理异常 - storageId: {}, userId: {}", storageId, userContext.getUserId(), e);
            ChatVO vo = new ChatVO();
            vo.setContent("抱歉，系统暂时无法处理您的请求，请稍后再试。");
            return vo;
        }
    }

    private ChatVO processWorkflow(String userMessage, UserContext userContext, String storageId, boolean authorized, List<Message> history) {
        String workFlowCode = userContext.getWorkFlowCode();
        storageId = buildStorageId(userContext);
        if (StringUtils.equalsAny(workFlowCode, "A02-1", "A05-1","A07","A09","A10","B06","B32")){
//            todo 根据不同流程编码查询文案
            return new ChatVO("");
        }
//        todo 校验参数
        if (StringUtils.equals(workFlowCode,"B29")){
            String content = "".replace("${alias}",userContext.getUserName())
                    .replace("${price}",userContext.getPrice())
                    .replace("${term}",userContext.getTerm());

        }

        return null;
    }

    private ChatVO chat(String userMessage, UserContext userContext, String storageId, boolean authorized, List<Message> history) {
        // 1. 输入安全检查和清理
        String sanitizedInput = promptInjectionFilter.sanitizeInput(userMessage);
        if (sanitizedInput == null || sanitizedInput.trim().isEmpty()) {
            ChatVO vo = new ChatVO();
            vo.setContent("请输入您的问题，我将为您提供帮助。");
            return vo;
        }
        // 2. 数字输入校验与转换
        NumericInputCheckResult numericCheck = applyNumericInputRules(storageId, authorized, history, sanitizedInput);
        if (numericCheck.earlyResponse() != null) {
            // 已在方法内记录对话历史，这里直接返回早期提示
            return numericCheck.earlyResponse();
        }
        sanitizedInput = numericCheck.processedInput();

        // 3. 检索：根据授权状态设置期望的 phase，并将“最新query + 历史对话”交给检索层（包含增强查询与批量检索）
        List<Document> documents = retrieveDocuments(storageId, sanitizedInput, authorized, history);
        if (documents.isEmpty()) {
            ChatVO vo = new ChatVO();
            vo.setContent("抱歉，未找到相关内容，请换个说法试试。");
            return vo;
        }
        // 传递 history 给重排序，使其可选择使用增强查询进行多路重排序融合
        // documents = rerankPostProcessor.process(retrievalQuery, documents);

        logRetrievedDocuments(documents);
        KnowledgeRecord record = DocumentParserUtils.parseDocumentToKnowledgeRecord(documents.get(0));

        ChatVO finalVO = new ChatVO();
        switch (record.getProcessingType()) {
            case DIRECT_ANSWER -> {
                finalVO.setContent(record.getAnswer());
                logger.info("【直接回答】query={} -> answer={}", sanitizedInput, finalVO.getContent());
            }
            case INTENT_ROUTING -> {
                logger.info("【意图路由】query={} -> 开始提取意图", sanitizedInput);

                String raw = chatClient.prompt()
                        .system(s -> {
                            s.param("example_query", record.getQuery());
                            s.param("example_cot", record.getCotThinking());
                            s.param("example_output", record.getAnswer());
                        })
                        .user(sanitizedInput)
                        .call()
                        .content();
                Map<String, Object> intentData = JsonExtractor.parseIntentAndParamsFromRawResponse(raw);
                String intent = (String) intentData.get("intent");
                @SuppressWarnings("unchecked") Map<String, Object> params = (Map<String, Object>) intentData.get("params");

                switch (intent) {
                    case "loan_application", "apply_loan" ->
                        // 借款：已授信→生成方案；未授信→开启授信
                            finalVO = authorized
                                    ? consumerLoanTools.generateLoanOffers(userContext, params)
                                    : consumerLoanTools.handleApplyCreditLimit();
                    case "query_credit_limit", "credit_apply", "apply_limit" ->
                        // 查询/申请额度：已授信→返回额度话术；未授信→开启授信
                            finalVO = authorized
                                    ? consumerLoanTools.handleQueryCreditLimit(userContext)
                                    : consumerLoanTools.handleApplyCreditLimit();
                    default ->
                        // 未识别的操作：直接回传模型JSON输出
                            finalVO.setContent(intentData.toString());
                }


            }
            default -> finalVO.setContent("未知的处理类型");
        }
        // 4. 持久化本次对话（用户问 & 助手答）
        chatMemory.add(storageId, Message.builder().type(Message.Type.USER).content(sanitizedInput).build());
        chatMemory.add(storageId, Message.builder().type(Message.Type.ASSISTANT).content(finalVO.getContent()).build());

        return finalVO;
    }

    private String buildStorageId(UserContext userContext) {
        String tenant = (userContext != null) ? userContext.getTenantCode() : null;
        String chatId = (userContext != null) ? userContext.getSessionId() : null;
        if (tenant != null && !tenant.isBlank()) {
            return tenant + ":" + chatId;
        }
        return chatId;
    }

    /**
     * 依据授权状态组装检索查询并执行检索。
     * 将最新 query 与历史对话、phase 一并传递给检索层。
     * 入参 storageId 为租户前缀的会话键（tenantCode:sessionId），用于在检索管线中关联会话历史。
     */
    private List<Document> retrieveDocuments(String storageId, String input, boolean authorizedForRetrieval, List<Message> history) {
        String desiredPhase = authorizedForRetrieval ? "post_credit" : "pre_credit";
        Map<String, Object> md = new HashMap<>();
        md.put("history", history);
        md.put("phase", desiredPhase);
        Query retrievalQuery = new Query(input, md, storageId);
        return documentRetriever.retrieve(retrievalQuery);
    }

    // 抽取：日志记录检索到的文档片段与元数据
    private void logRetrievedDocuments(List<Document> documents) {
        for (int i = 0; i < documents.size(); i++) {
            Document d = documents.get(i);
            String text = d != null ? d.getText() : "";
            String snippet = text == null ? "" : (text.length() > 200 ? text.substring(0, 200) + "..." : text);
            logger.info("文档#{} | 内容片段=\"{}\" | metadata={}", i + 1, snippet, d != null ? d.metadata() : Map.of());
        }
    }

    /**
     * 数字输入校验与转换的总控方法（抽取自 chat() 中的逻辑片段）。
     * <p>
     * 处理顺序与规则说明：
     * 0) 授信拦截（仅在“未授信且用户输入为纯数字”时触发）：
     * - 未授信且输入为纯数字 → 直接开启授信流程并早退；非纯数字则继续后续校验与转换。
     * 1) 首条纯数字：若该会话的首个用户消息的第一句仅包含数字，则在该句前自动加上“借”前缀。
     * - 例如："1000" → "借1000"；"1000。我要分期" → "借1000。我要分期"。
     * - 第一句的边界以常见标点或换行符判定：。！？.!?\n\r。
     * 2) 非首条且纯数字的早期提示：
     * - 若输入为纯数字且在 16..99 之间，返回提示语，要求用户重新输入需求，避免无效金额或分期数。
     * - 在触发提示时，本方法会统一写入对话历史（用户输入与助手提示），并通过 earlyResponse 返回。
     * 3) 非首条且纯数字的转换：
     * - 1..15 → 转为“分{n}期”；>100 → 前缀为“借{n}”。
     * - 其余情况（含 0、<=15 非正、=100、非数字）保持原样。
     * <p>
     * 返回值约定：
     * - 若需要直接提示用户（早退），则 earlyResponse 非空，processedInput 为触发提示时的用户输入版本；
     * - 若不需要早退，则 earlyResponse 为空，processedInput 为校验/转换后的输入。
     * <p>
     * 设计说明：
     * - 抽取为独立方法，便于集中维护数字相关的业务规则，减少 chat() 的分支复杂度；
     * - 本方法内部负责在早退场景下统一记录聊天历史，调用方只需根据 earlyResponse 是否为空决定是否返回即可。
     */
    private NumericInputCheckResult applyNumericInputRules(String storageId, boolean authorized, List<Message> history, String input) {
        try {
            // Step 0: 授信状态优先级判断（仅拦截纯数字输入）
            String trimmed = input == null ? "" : input.trim();
            boolean isDigitsOnly = trimmed.matches("\\d+");
            if (!authorized && isDigitsOnly) {
                ChatVO early = consumerLoanTools.handleApplyCreditLimit();
                chatMemory.add(storageId, Message.builder().type(Message.Type.USER).content(input).build());
                chatMemory.add(storageId, Message.builder().type(Message.Type.ASSISTANT).content(early.getContent()).build());
                return new NumericInputCheckResult(input, early);
            }

            // Step 1: 首条纯数字 → 第一句自动加“借”前缀
            String updated = maybeAutoPrefixBorrowIfFirstNumericSentence(history, input);

            // Step 2: 非首条且纯数字 → 16..99 返回早期提示（并写入聊天历史）
            String earlyResponse = maybeEarlyResponseForNonFirstNumericInput(history, updated);
            if (earlyResponse != null) {
                chatMemory.add(storageId, Message.builder().type(Message.Type.USER).content(updated).build());
                chatMemory.add(storageId, Message.builder().type(Message.Type.ASSISTANT).content(earlyResponse).build());
                ChatVO vo = new ChatVO();
                vo.setContent(earlyResponse);
                return new NumericInputCheckResult(updated, vo);
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
    private record NumericInputCheckResult(String processedInput, ChatVO earlyResponse) {
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

            int boundary = -1;
            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?' || c == '\n' || c == '\r') {
                    boundary = i;
                    break;
                }
            }
            String first = boundary == -1 ? input : input.substring(0, boundary);
            String normalized = first.replaceAll("[\\s\\p{Punct}，。！？、；：—-]", "");
            if (!normalized.isEmpty() && normalized.matches("\\d+")) {
                String updated = "借" + first + (boundary == -1 ? "" : input.substring(boundary));
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


}

