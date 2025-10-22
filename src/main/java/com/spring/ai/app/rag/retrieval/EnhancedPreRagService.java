package com.spring.ai.app.rag.retrieval;

import com.spring.ai.app.rag.intent.IntentSlotExtractionService;
import com.spring.ai.app.rag.model.UserContext;
import com.spring.ai.app.rag.reranker.RerankService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 增强的前置RAG召回服务
 * 实现高阶方案D：升级前置RAG召回能力
 * 
 * 功能特性：
 * 1. 多路召回策略（向量+BM25+混合）
 * 2. 查询扩展和重写
 * 3. 意图感知的检索优化
 * 4. 智能重排序和融合
 * 5. 上下文增强
 * 
 * @author LHY
 * @date 2025-01-XX
 */
@Service
public class EnhancedPreRagService {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedPreRagService.class);

    private final DocumentRetriever vectorRetriever;
    private final DocumentRetriever bm25Retriever;
    private final DocumentRetriever hybridRetriever;
    private final RerankService rerankService;
    private final QueryExpansionService queryExpansionService;

    @Value("${spring.ai.rag.enhanced.multi-recall.enabled:true}")
    private boolean multiRecallEnabled;

    @Value("${spring.ai.rag.enhanced.multi-recall.top-k:10}")
    private int multiRecallTopK;

    @Value("${spring.ai.rag.enhanced.rerank.enabled:true}")
    private boolean rerankEnabled;

    @Value("${spring.ai.rag.enhanced.rerank.top-k:5}")
    private int rerankTopK;

    @Value("${spring.ai.rag.enhanced.query-expansion.enabled:true}")
    private boolean queryExpansionEnabled;

    public EnhancedPreRagService(
            @Qualifier("vectorStoreDocumentRetriever") DocumentRetriever vectorRetriever,
            @Qualifier("BM25DocumentRetriever") DocumentRetriever bm25Retriever,
            @Qualifier("hybridDocumentRetriever") DocumentRetriever hybridRetriever,
            RerankService rerankService,
            QueryExpansionService queryExpansionService) {
        this.vectorRetriever = vectorRetriever;
        this.bm25Retriever = bm25Retriever;
        this.hybridRetriever = hybridRetriever;
        this.rerankService = rerankService;
        this.queryExpansionService = queryExpansionService;
    }

    /**
     * 增强的文档检索
     * 
     * @param originalQuery 原始查询
     * @param intentResult 意图识别结果
     * @param userContext 用户上下文
     * @return 增强检索结果
     */
    public EnhancedRetrievalResult enhancedRetrieve(Query originalQuery, 
                                                   IntentSlotExtractionService.IntentSlotExtractionResult intentResult,
                                                   UserContext userContext) {
        try {
            // 1. 查询预处理和扩展
            List<Query> expandedQueries = preprocessAndExpandQuery(originalQuery, intentResult, userContext);
            
            // 2. 多路召回
            List<Document> multiRecallResults = performMultiRecall(expandedQueries);
            
            // 3. 去重和初步过滤
            List<Document> deduplicatedResults = deduplicateAndFilter(multiRecallResults, intentResult);
            
            // 4. 重排序
            List<Document> rerankedResults = performReranking(originalQuery, deduplicatedResults);
            
            // 5. 上下文增强
            List<Document> enhancedResults = enhanceWithContext(rerankedResults, userContext, intentResult);
            
            // 6. 构建结果
            return new EnhancedRetrievalResult(
                enhancedResults,
                expandedQueries,
                multiRecallResults.size(),
                deduplicatedResults.size(),
                rerankedResults.size(),
                calculateRetrievalMetrics(originalQuery, enhancedResults)
            );
            
        } catch (Exception e) {
            logger.error("增强检索失败，回退到基础检索 - query: {}", originalQuery.text(), e);
            // 回退到基础检索
            return createFallbackResult(originalQuery);
        }
    }

    /**
     * 查询预处理和扩展
     */
    private List<Query> preprocessAndExpandQuery(Query originalQuery, 
                                               IntentSlotExtractionService.IntentSlotExtractionResult intentResult,
                                               UserContext userContext) {
        List<Query> queries = new ArrayList<>();
        queries.add(originalQuery); // 保留原始查询
        
        if (!queryExpansionEnabled) {
            return queries;
        }
        
        try {
            // 基于意图的查询扩展
            List<String> expandedQueries = queryExpansionService.expandQuery(
                originalQuery.text(), 
                intentResult.getIntentResult().intent(),
                intentResult.getIntentResult().slots(),
                userContext
            );
            
            for (String expandedQuery : expandedQueries) {
                queries.add(Query.builder().text(expandedQuery).build());
            }
            
            logger.debug("查询扩展完成 - 原始查询: {}, 扩展查询数: {}", 
                originalQuery.text(), expandedQueries.size());
            
        } catch (Exception e) {
            logger.warn("查询扩展失败，使用原始查询 - query: {}", originalQuery.text(), e);
        }
        
        return queries;
    }

    /**
     * 多路召回
     */
    private List<Document> performMultiRecall(List<Query> queries) {
        if (!multiRecallEnabled) {
            // 如果禁用多路召回，只使用混合检索
            return hybridRetriever.retrieve(queries.get(0));
        }
        
        Set<Document> allResults = new LinkedHashSet<>(); // 保持顺序并去重
        
        for (Query query : queries) {
            try {
                // 向量检索
                List<Document> vectorResults = vectorRetriever.retrieve(query);
                allResults.addAll(vectorResults.stream()
                    .limit(multiRecallTopK / 3)
                    .collect(Collectors.toList()));
                
                // BM25检索
                List<Document> bm25Results = bm25Retriever.retrieve(query);
                allResults.addAll(bm25Results.stream()
                    .limit(multiRecallTopK / 3)
                    .collect(Collectors.toList()));
                
                // 混合检索
                List<Document> hybridResults = hybridRetriever.retrieve(query);
                allResults.addAll(hybridResults.stream()
                    .limit(multiRecallTopK / 3)
                    .collect(Collectors.toList()));
                
            } catch (Exception e) {
                logger.warn("多路召回部分失败 - query: {}", query.text(), e);
            }
        }
        
        logger.debug("多路召回完成 - 总结果数: {}", allResults.size());
        return new ArrayList<>(allResults);
    }

    /**
     * 去重和初步过滤
     */
    private List<Document> deduplicateAndFilter(List<Document> documents, 
                                              IntentSlotExtractionService.IntentSlotExtractionResult intentResult) {
        // 基于内容去重
        Map<String, Document> uniqueDocuments = new LinkedHashMap<>();
        
        for (Document doc : documents) {
            String content = doc.getText();
            String contentHash = Integer.toString(content.hashCode());
            
            if (!uniqueDocuments.containsKey(contentHash)) {
                // 基于意图的相关性过滤
                if (isRelevantToIntent(doc, intentResult)) {
                    uniqueDocuments.put(contentHash, doc);
                }
            }
        }
        
        List<Document> result = new ArrayList<>(uniqueDocuments.values());
        logger.debug("去重过滤完成 - 原始: {}, 过滤后: {}", documents.size(), result.size());
        
        return result;
    }

    /**
     * 基于意图的相关性判断
     */
    private boolean isRelevantToIntent(Document document, 
                                     IntentSlotExtractionService.IntentSlotExtractionResult intentResult) {
        String intent = intentResult.getIntentResult().intent();
        String content = document.getText().toLowerCase();
        Map<String, String> slots = intentResult.getIntentResult().slots();
        
        // 基于意图类型的关键词匹配
        switch (intent) {
            case "INQUIRY":
                return content.contains("利息") || content.contains("费用") || 
                       content.contains("额度") || content.contains("条件") ||
                       content.contains("流程") || content.contains("还款");
                       
            case "OPERATION":
                return content.contains("申请") || content.contains("借款") || 
                       content.contains("贷款") || content.contains("授信");
                       
            case "CLARIFICATION":
                return content.contains("金额") || content.contains("期数") || 
                       content.contains("用途") || content.contains("方案");
                       
            default:
                return true; // OTHER类型不过滤
        }
    }

    /**
     * 重排序
     */
    private List<Document> performReranking(Query originalQuery, List<Document> documents) {
        if (!rerankEnabled || documents.size() <= rerankTopK) {
            return documents.stream().limit(rerankTopK).collect(Collectors.toList());
        }
        
        try {
            // 将Document列表转换为文本列表进行重排序
            List<String> documentTexts = documents.stream()
                .map(Document::getText)
                .collect(Collectors.toList());
            
            List<RerankService.ResultItem> rerankResults = rerankService.rerank(originalQuery.text(), documentTexts);
            
            // 根据重排序结果重新排列原始文档
            List<Document> rerankedDocuments = new ArrayList<>();
            Set<Integer> usedIndices = new HashSet<>();
            
            for (RerankService.ResultItem item : rerankResults) {
                if (item.index >= 0 && item.index < documents.size() && usedIndices.add(item.index)) {
                    rerankedDocuments.add(documents.get(item.index));
                }
            }
            
            // 添加未被重排序的文档
            for (int i = 0; i < documents.size(); i++) {
                if (!usedIndices.contains(i)) {
                    rerankedDocuments.add(documents.get(i));
                }
            }
            
            logger.debug("重排序完成 - 原始: {}, 重排序后: {}", documents.size(), rerankedDocuments.size());
            return rerankedDocuments.stream().limit(rerankTopK).collect(Collectors.toList());
            
        } catch (Exception e) {
            logger.warn("重排序失败，使用原始顺序 - query: {}", originalQuery.text(), e);
            return documents.stream().limit(rerankTopK).collect(Collectors.toList());
        }
    }

    /**
     * 上下文增强
     */
    private List<Document> enhanceWithContext(List<Document> documents, 
                                            UserContext userContext,
                                            IntentSlotExtractionService.IntentSlotExtractionResult intentResult) {
        return documents.stream()
            .map(doc -> enhanceDocumentWithContext(doc, userContext, intentResult))
            .collect(Collectors.toList());
    }

    /**
     * 单个文档的上下文增强
     */
    private Document enhanceDocumentWithContext(Document document, 
                                              UserContext userContext,
                                              IntentSlotExtractionService.IntentSlotExtractionResult intentResult) {
        Map<String, Object> enhancedMetadata = new HashMap<>(document.getMetadata());
        
        // 添加意图信息
        enhancedMetadata.put("detected_intent", intentResult.getIntentResult().intent());
        enhancedMetadata.put("intent_confidence", intentResult.getIntentResult().confidence());
        
        // 添加用户上下文
        if (userContext != null) {
            if (userContext.getAuthorized() != null) {
                enhancedMetadata.put("user_authorized", userContext.getAuthorized());
            }
            if (userContext.getAvailableCredit() != null) {
                enhancedMetadata.put("user_available_credit", userContext.getAvailableCredit());
            }
        }
        
        // 添加槽位信息
        Map<String, String> slots = intentResult.getIntentResult().slots();
        if (!slots.isEmpty()) {
            enhancedMetadata.put("extracted_slots", slots);
        }
        
        return Document.builder()
            .text(document.getText())
            .metadata(enhancedMetadata)
            .build();
    }

    /**
     * 计算检索指标
     */
    private RetrievalMetrics calculateRetrievalMetrics(Query originalQuery, List<Document> results) {
        return new RetrievalMetrics(
            results.size(),
            calculateAverageRelevanceScore(results),
            calculateCoverageScore(originalQuery, results),
            calculateDiversityScore(results)
        );
    }

    private double calculateAverageRelevanceScore(List<Document> results) {
        // 简化的相关性评分计算
        return results.stream()
            .mapToDouble(doc -> {
                Object score = doc.getMetadata().getOrDefault("relevance_score", 0.5);
                if (score instanceof Number) {
                    return ((Number) score).doubleValue();
                }
                return 0.5;
            })
            .average()
            .orElse(0.5);
    }

    private double calculateCoverageScore(Query query, List<Document> results) {
        // 简化的覆盖度评分计算
        String[] queryTerms = query.text().toLowerCase().split("\\s+");
        long coveredTerms = Arrays.stream(queryTerms)
            .mapToLong(term -> results.stream()
                .mapToLong(doc -> doc.getText().toLowerCase().contains(term) ? 1 : 0)
                .sum())
            .sum();
        return (double) coveredTerms / (queryTerms.length * results.size());
    }

    private double calculateDiversityScore(List<Document> results) {
        // 简化的多样性评分计算
        Set<String> uniqueTopics = results.stream()
            .map(doc -> doc.getMetadata().getOrDefault("topic", "unknown").toString())
            .collect(Collectors.toSet());
        return (double) uniqueTopics.size() / Math.max(results.size(), 1);
    }

    /**
     * 创建回退结果
     */
    private EnhancedRetrievalResult createFallbackResult(Query originalQuery) {
        List<Document> fallbackResults = hybridRetriever.retrieve(originalQuery);
        return new EnhancedRetrievalResult(
            fallbackResults,
            List.of(originalQuery),
            fallbackResults.size(),
            fallbackResults.size(),
            fallbackResults.size(),
            new RetrievalMetrics(fallbackResults.size(), 0.5, 0.5, 0.5)
        );
    }

    /**
     * 增强检索结果
     */
    public static class EnhancedRetrievalResult {
        private final List<Document> documents;
        private final List<Query> expandedQueries;
        private final int multiRecallCount;
        private final int deduplicatedCount;
        private final int rerankedCount;
        private final RetrievalMetrics metrics;

        public EnhancedRetrievalResult(List<Document> documents, 
                                     List<Query> expandedQueries,
                                     int multiRecallCount, 
                                     int deduplicatedCount, 
                                     int rerankedCount,
                                     RetrievalMetrics metrics) {
            this.documents = documents;
            this.expandedQueries = expandedQueries;
            this.multiRecallCount = multiRecallCount;
            this.deduplicatedCount = deduplicatedCount;
            this.rerankedCount = rerankedCount;
            this.metrics = metrics;
        }

        // Getters
        public List<Document> getDocuments() { return documents; }
        public List<Query> getExpandedQueries() { return expandedQueries; }
        public int getMultiRecallCount() { return multiRecallCount; }
        public int getDeduplicatedCount() { return deduplicatedCount; }
        public int getRerankedCount() { return rerankedCount; }
        public RetrievalMetrics getMetrics() { return metrics; }
    }

    /**
     * 检索指标
     */
    public static class RetrievalMetrics {
        private final int totalResults;
        private final double averageRelevance;
        private final double coverage;
        private final double diversity;

        public RetrievalMetrics(int totalResults, double averageRelevance, double coverage, double diversity) {
            this.totalResults = totalResults;
            this.averageRelevance = averageRelevance;
            this.coverage = coverage;
            this.diversity = diversity;
        }

        // Getters
        public int getTotalResults() { return totalResults; }
        public double getAverageRelevance() { return averageRelevance; }
        public double getCoverage() { return coverage; }
        public double getDiversity() { return diversity; }
    }
}