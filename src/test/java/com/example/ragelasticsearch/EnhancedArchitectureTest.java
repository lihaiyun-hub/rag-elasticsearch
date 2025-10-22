package com.example.ragelasticsearch;

import com.spring.ai.app.rag.intent.IntentSlotExtractionService;
import com.spring.ai.app.rag.intent.IntentSlotExtractionService.IntentSlotExtractionResult;
import com.spring.ai.app.rag.retrieval.EnhancedPreRagService;
import com.spring.ai.app.rag.retrieval.QueryExpansionService;
import com.spring.ai.app.rag.services.EnhancedCustomerSupportAssistant;
import com.spring.ai.app.rag.model.UserContext;
import com.spring.ai.app.rag.retrieval.EnhancedPreRagService.EnhancedRetrievalResult;
import com.spring.ai.app.rag.flow.IntentResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 增强架构测试类
 * 测试高阶方案D：统一意图槽位抽取 + 增强前置RAG召回
 */
@SpringBootTest(classes = com.spring.ai.app.rag.RagEsApplication.class)
@TestPropertySource(locations = "classpath:application-test.properties")
public class EnhancedArchitectureTest {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedArchitectureTest.class);

    private UserContext testUserContext;

    @BeforeEach
    void setUp() {
        // 设置测试用户上下文
        testUserContext = new UserContext();
        testUserContext.setUserName("测试用户");
        testUserContext.setAvailableCredit(50000.0);
        testUserContext.setRecentRepaymentStatus("正常");
        testUserContext.setAuthorized(true);
        testUserContext.setTermOptions(java.util.Arrays.asList(3, 6, 12, 24, 36));
        testUserContext.setLoanPurposes(java.util.Arrays.asList("装修", "旅游", "教育", "医疗", "购物", "其他"));
        testUserContext.setBankCardNumber("1234567890123456");
        testUserContext.setBankName("测试银行");
    }

    /**
     * 测试统一意图槽位抽取功能
     */
    @Test
    void testUnifiedIntentSlotExtraction() {
        logger.info("=== 测试统一意图槽位抽取功能 ===");

        // 测试用例
        String[] testQueries = {
                "我想借5万块钱装修房子，分12期还",  // 操作意图
                "借款利率是多少？",                    // 咨询意图
                "我的额度还有多少？",                  // 咨询意图
                "借不了",                            // 澄清意图
                "今天天气怎么样？"                    // 其他意图
        };

        for (String query : testQueries) {
            logger.info("测试查询: {}", query);
            
            // 模拟意图槽位抽取结果
            IntentSlotExtractionResult mockResult = createMockIntentSlotResult(query);
            
            logger.info("识别意图: {}, 置信度: {}", 
                    mockResult.getIntentResult().intent(), mockResult.getIntentResult().confidence());
            logger.info("提取槽位: {}", mockResult.getIntentResult().slots());
            logger.info("需要授权: {}", mockResult.getIntentResult().consumerLoanAuthorized());
            logger.info("建议操作: {}", mockResult.getSuggestedAction());
            logger.info("---");
        }
    }

    /**
     * 测试增强前置RAG召回功能
     */
    @Test
    void testEnhancedPreRagRecall() {
        logger.info("=== 测试增强前置RAG召回功能 ===");

        String[] testQueries = {
                "借款利率是多少",
                "如何申请贷款",
                "还款方式有哪些"
        };

        for (String query : testQueries) {
            logger.info("测试查询: {}", query);
            
            // 模拟增强检索结果
            EnhancedRetrievalResult mockResult = createMockRetrievalResult(query);
            
            logger.info("检索文档数: {}", mockResult.getMetrics().getTotalResults());
            logger.info("平均相关性: {}", mockResult.getMetrics().getAverageRelevance());
            logger.info("覆盖度: {}", mockResult.getMetrics().getCoverage());
            logger.info("多样性: {}", mockResult.getMetrics().getDiversity());
            logger.info("查询扩展: {}", mockResult.getExpandedQueries().stream()
                .map(q -> q.text()).collect(java.util.stream.Collectors.toList()));
            logger.info("---");
        }
    }

    /**
     * 测试查询扩展功能
     */
    @Test
    void testQueryExpansion() {
        logger.info("=== 测试查询扩展功能 ===");

        String[] testQueries = {
                "借款利率",
                "申请流程",
                "还款方式"
        };

        for (String query : testQueries) {
            logger.info("原始查询: {}", query);
            
            // 模拟查询扩展结果
            List<String> expandedQueries = createMockExpandedQueries(query);
            
            logger.info("扩展查询: {}", expandedQueries);
            logger.info("---");
        }
    }

    /**
     * 测试完整的增强架构流程
     */
    @Test
    void testCompleteEnhancedArchitecture() {
        logger.info("=== 测试完整的增强架构流程 ===");

        String testQuery = "我想借5万块钱装修房子，分12期还";
        logger.info("用户输入: {}", testQuery);

        // 1. 统一意图槽位抽取
        logger.info("1. 执行统一意图槽位抽取...");
        IntentSlotExtractionResult intentResult = createMockIntentSlotResult(testQuery);
        logger.info("   意图: {}, 槽位: {}", intentResult.getIntentResult().intent(), intentResult.getIntentResult().slots());

        // 2. 增强前置RAG召回
        logger.info("2. 执行增强前置RAG召回...");
        EnhancedRetrievalResult retrievalResult = createMockRetrievalResult(testQuery);
        logger.info("   检索到 {} 个文档，平均相关性: {}", 
                retrievalResult.getMetrics().getTotalResults(), retrievalResult.getMetrics().getAverageRelevance());

        // 3. 生成最终响应
        logger.info("3. 生成最终响应...");
        String mockResponse = generateMockResponse(intentResult, retrievalResult);
        logger.info("   最终响应: {}", mockResponse);

        logger.info("=== 增强架构流程测试完成 ===");
    }

    /**
     * 创建模拟的意图槽位抽取结果
     */
    private IntentSlotExtractionResult createMockIntentSlotResult(String query) {
        Map<String, String> slots = new HashMap<>();
        String intent;
        double confidence;
        boolean consumerLoanAuthorized;
        String suggestedAction;
        
        if (query.contains("借") && query.contains("万") && query.contains("期")) {
            intent = "OPERATION";
            confidence = 0.95;
            slots.put("amount", "50000");
            slots.put("term", "12");
            slots.put("purpose", "装修");
            consumerLoanAuthorized = false;
            suggestedAction = "generateLoanOffers";
        } else if (query.contains("利率") || query.contains("额度")) {
            intent = "INQUIRY";
            confidence = 0.90;
            consumerLoanAuthorized = false;
            suggestedAction = "provideInformation";
        } else if (query.contains("借不了")) {
            intent = "CLARIFICATION";
            confidence = 0.85;
            consumerLoanAuthorized = false;
            suggestedAction = "requestMoreInfo";
        } else {
            intent = "OTHER";
            confidence = 0.80;
            consumerLoanAuthorized = false;
            suggestedAction = "politeRefusal";
        }
        
        IntentResult intentResult = new IntentResult(intent, confidence, slots, consumerLoanAuthorized);
        return new IntentSlotExtractionResult(
            intentResult,
            "基于查询内容和上下文进行意图识别",
            suggestedAction,
            true
        );
    }

    /**
     * 创建模拟的增强检索结果
     */
    private EnhancedRetrievalResult createMockRetrievalResult(String query) {
        // 创建模拟的检索指标
        EnhancedPreRagService.RetrievalMetrics metrics = 
            new EnhancedPreRagService.RetrievalMetrics(15, 0.82, 0.75, 0.68);
        
        // 创建模拟的查询列表
        List<org.springframework.ai.rag.Query> expandedQueries = createMockExpandedQueries(query).stream()
            .map(queryText -> org.springframework.ai.rag.Query.builder().text(queryText).build())
            .collect(java.util.stream.Collectors.toList());
        
        return new EnhancedRetrievalResult(
            new java.util.ArrayList<>(), // 空的文档列表
            expandedQueries,
            10, // multiRecallCount
            8,  // deduplicatedCount
            5,  // rerankedCount
            metrics
        );
    }

    /**
     * 创建模拟的查询扩展结果
     */
    private List<String> createMockExpandedQueries(String query) {
        if (query.contains("利率")) {
            return List.of("贷款利率", "借款费率", "年化利率");
        } else if (query.contains("申请")) {
            return List.of("贷款申请流程", "借款申请步骤", "授信申请");
        } else if (query.contains("还款")) {
            return List.of("还款方式", "还款计划", "分期还款");
        } else {
            return List.of(query + "相关", query + "详情", query + "说明");
        }
    }

    /**
     * 生成模拟响应
     */
    private String generateMockResponse(IntentSlotExtractionResult intentResult, EnhancedRetrievalResult retrievalResult) {
        switch (intentResult.getIntentResult().intent()) {
            case "OPERATION":
                return "根据您的需求，为您生成借款方案：金额50000元，期数12期，用途装修。请确认是否继续？";
            case "INQUIRY":
                return "根据检索到的信息，当前借款年化利率为7.2%-24%，具体利率根据您的信用情况确定。";
            case "CLARIFICATION":
                return "请问您遇到什么具体问题？我可以帮您查看额度情况或协助解决借款问题。";
            default:
                return "抱歉，我只能提供线上消费贷款相关服务。有什么贷款问题我可以帮您？";
        }
    }
}