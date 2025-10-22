package com.spring.ai.app.rag.retrieval;

import com.spring.ai.app.rag.model.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 查询扩展服务
 * 基于意图、槽位和用户上下文进行智能查询扩展
 * 
 * @author LHY
 * @date 2025-01-XX
 */
@Service
public class QueryExpansionService {

    private static final Logger logger = LoggerFactory.getLogger(QueryExpansionService.class);

    private final ChatClient chatClient;
    private final PromptTemplate queryExpansionPrompt;

    @Value("${spring.ai.rag.enhanced.query-expansion.max-expansions:3}")
    private int maxExpansions;

    @Value("${spring.ai.rag.enhanced.query-expansion.use-llm:true}")
    private boolean useLlmExpansion;

    @Value("${spring.ai.rag.enhanced.query-expansion.use-rules:true}")
    private boolean useRuleBasedExpansion;

    public QueryExpansionService(ChatClient.Builder chatClientBuilder,
                               @Value("classpath:/prompts/query-expansion-prompt.st") Resource promptResource) {
        this.chatClient = chatClientBuilder.build();
        this.queryExpansionPrompt = new PromptTemplate(promptResource);
    }

    /**
     * 扩展查询
     * 
     * @param originalQuery 原始查询
     * @param intent 意图
     * @param slots 槽位
     * @param userContext 用户上下文
     * @return 扩展后的查询列表
     */
    public List<String> expandQuery(String originalQuery, 
                                   String intent, 
                                   Map<String, String> slots,
                                   UserContext userContext) {
        Set<String> expandedQueries = new LinkedHashSet<>();
        
        try {
            // 1. 基于规则的扩展
            if (useRuleBasedExpansion) {
                List<String> ruleBasedExpansions = performRuleBasedExpansion(originalQuery, intent, slots);
                expandedQueries.addAll(ruleBasedExpansions);
            }
            
            // 2. 基于LLM的扩展
            if (useLlmExpansion) {
                List<String> llmExpansions = performLlmBasedExpansion(originalQuery, intent, slots, userContext);
                expandedQueries.addAll(llmExpansions);
            }
            
            // 3. 限制扩展数量
            List<String> result = expandedQueries.stream()
                .limit(maxExpansions)
                .collect(Collectors.toList());
            
            logger.debug("查询扩展完成 - 原始: {}, 扩展: {}", originalQuery, result);
            return result;
            
        } catch (Exception e) {
            logger.error("查询扩展失败 - query: {}", originalQuery, e);
            return Collections.emptyList();
        }
    }

    /**
     * 基于规则的查询扩展
     */
    private List<String> performRuleBasedExpansion(String originalQuery, String intent, Map<String, String> slots) {
        List<String> expansions = new ArrayList<>();
        String lowerQuery = originalQuery.toLowerCase();
        
        // 基于意图的扩展规则
        switch (intent) {
            case "INQUIRY":
                expansions.addAll(expandInquiryQuery(lowerQuery, slots));
                break;
            case "OPERATION":
                expansions.addAll(expandOperationQuery(lowerQuery, slots));
                break;
            case "CLARIFICATION":
                expansions.addAll(expandClarificationQuery(lowerQuery, slots));
                break;
        }
        
        // 通用扩展规则
        expansions.addAll(expandWithSynonyms(lowerQuery));
        expansions.addAll(expandWithRelatedTerms(lowerQuery));
        
        return expansions.stream()
            .filter(query -> !query.equals(originalQuery))
            .distinct()
            .collect(Collectors.toList());
    }

    /**
     * 扩展咨询类查询
     */
    private List<String> expandInquiryQuery(String query, Map<String, String> slots) {
        List<String> expansions = new ArrayList<>();
        
        // 利息相关扩展
        if (query.contains("利息") || query.contains("利率")) {
            expansions.add("年化利率是多少");
            expansions.add("日利率计算方式");
            expansions.add("利息费用标准");
        }
        
        // 额度相关扩展
        if (query.contains("额度") || query.contains("能借多少")) {
            expansions.add("最高授信额度");
            expansions.add("可用额度查询");
            expansions.add("额度评估标准");
        }
        
        // 条件相关扩展
        if (query.contains("条件") || query.contains("要求")) {
            expansions.add("申请资格要求");
            expansions.add("准入条件说明");
            expansions.add("审核标准");
        }
        
        // 基于槽位的扩展
        String amount = slots.get("amount");
        if (amount != null) {
            expansions.add(amount + "元贷款条件");
            expansions.add(amount + "额度申请");
        }
        
        String term = slots.get("term");
        if (term != null) {
            expansions.add(term + "期还款方式");
            expansions.add(term + "个月贷款利率");
        }
        
        return expansions;
    }

    /**
     * 扩展操作类查询
     */
    private List<String> expandOperationQuery(String query, Map<String, String> slots) {
        List<String> expansions = new ArrayList<>();
        
        // 申请相关扩展
        if (query.contains("申请") || query.contains("借款")) {
            expansions.add("贷款申请流程");
            expansions.add("在线申请步骤");
            expansions.add("申请材料准备");
        }
        
        // 提额相关扩展
        if (query.contains("提额") || query.contains("增加额度")) {
            expansions.add("额度提升申请");
            expansions.add("提额条件要求");
            expansions.add("额度调整流程");
        }
        
        // 还款相关扩展
        if (query.contains("还款") || query.contains("还钱")) {
            expansions.add("还款方式选择");
            expansions.add("提前还款流程");
            expansions.add("还款计划调整");
        }
        
        return expansions;
    }

    /**
     * 扩展澄清类查询
     */
    private List<String> expandClarificationQuery(String query, Map<String, String> slots) {
        List<String> expansions = new ArrayList<>();
        
        // 金额澄清扩展
        if (query.contains("多少钱") || query.contains("金额")) {
            expansions.add("具体借款金额");
            expansions.add("贷款数额确认");
        }
        
        // 期数澄清扩展
        if (query.contains("多少期") || query.contains("期数")) {
            expansions.add("还款期数选择");
            expansions.add("分期方案确认");
        }
        
        // 用途澄清扩展
        if (query.contains("用途") || query.contains("干什么")) {
            expansions.add("资金使用用途");
            expansions.add("贷款目的说明");
        }
        
        return expansions;
    }

    /**
     * 同义词扩展
     */
    private List<String> expandWithSynonyms(String query) {
        List<String> expansions = new ArrayList<>();
        
        // 贷款相关同义词
        Map<String, List<String>> synonyms = Map.of(
            "贷款", List.of("借款", "信贷", "融资"),
            "利息", List.of("利率", "费用", "成本"),
            "额度", List.of("限额", "授信", "可贷金额"),
            "申请", List.of("办理", "开通", "激活"),
            "还款", List.of("还钱", "偿还", "归还")
        );
        
        for (Map.Entry<String, List<String>> entry : synonyms.entrySet()) {
            if (query.contains(entry.getKey())) {
                for (String synonym : entry.getValue()) {
                    String expandedQuery = query.replace(entry.getKey(), synonym);
                    if (!expandedQuery.equals(query)) {
                        expansions.add(expandedQuery);
                    }
                }
            }
        }
        
        return expansions;
    }

    /**
     * 相关术语扩展
     */
    private List<String> expandWithRelatedTerms(String query) {
        List<String> expansions = new ArrayList<>();
        
        // 相关术语映射
        Map<String, List<String>> relatedTerms = Map.of(
            "贷款", List.of("个人信贷", "消费贷", "信用贷"),
            "利率", List.of("年化利率", "日利率", "月利率"),
            "还款", List.of("等额本息", "等额本金", "按月付息"),
            "审核", List.of("风控", "征信", "评估")
        );
        
        for (Map.Entry<String, List<String>> entry : relatedTerms.entrySet()) {
            if (query.contains(entry.getKey())) {
                for (String relatedTerm : entry.getValue()) {
                    expansions.add(query + " " + relatedTerm);
                }
            }
        }
        
        return expansions;
    }

    /**
     * 基于LLM的查询扩展
     */
    private List<String> performLlmBasedExpansion(String originalQuery, 
                                                String intent, 
                                                Map<String, String> slots,
                                                UserContext userContext) {
        try {
            Map<String, Object> promptVariables = new HashMap<>();
            promptVariables.put("original_query", originalQuery);
            promptVariables.put("intent", intent);
            promptVariables.put("slots", formatSlots(slots));
            promptVariables.put("user_context", formatUserContext(userContext));
            promptVariables.put("max_expansions", maxExpansions);
            
            String response = chatClient.prompt(queryExpansionPrompt.create(promptVariables))
                .call()
                .content();
            
            return parseExpansionResponse(response);
            
        } catch (Exception e) {
            logger.error("LLM查询扩展失败 - query: {}", originalQuery, e);
            return Collections.emptyList();
        }
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
     * 格式化用户上下文
     */
    private String formatUserContext(UserContext userContext) {
        if (userContext == null) {
            return "无";
        }
        
        List<String> contextInfo = new ArrayList<>();
        
        if (userContext.getAuthorized() != null) {
            contextInfo.add("授权状态: " + (userContext.getAuthorized() ? "已授权" : "未授权"));
        }
        
        if (userContext.getAvailableCredit() != null) {
            contextInfo.add("可用额度: " + userContext.getAvailableCredit());
        }
        
        return contextInfo.isEmpty() ? "无" : String.join(", ", contextInfo);
    }

    /**
     * 解析扩展响应
     */
    private List<String> parseExpansionResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        // 简单的行分割解析
        return Arrays.stream(response.split("\n"))
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .limit(maxExpansions)
            .collect(Collectors.toList());
    }
}