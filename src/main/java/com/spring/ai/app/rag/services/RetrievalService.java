package com.spring.ai.app.rag.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.Query;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 独立的检索服务
 * 负责文档检索逻辑，与大模型调用解耦
 * 
 * @author LHY
 * @date 2025-01-20
 */
@Service
public class RetrievalService {

    private static final Logger logger = LoggerFactory.getLogger(RetrievalService.class);
    
    private final DocumentRetriever documentRetriever;
    private final List<QueryTransformer> queryTransformers;

    public RetrievalService(@Qualifier("selectedDocumentRetriever") DocumentRetriever documentRetriever,
                           List<QueryTransformer> queryTransformers) {
        this.documentRetriever = documentRetriever;
        this.queryTransformers = queryTransformers;
    }

    /**
     * 执行文档检索
     * 
     * @param query 原始查询
     * @param chatId 会话ID，用于上下文查询重写
     * @return 检索到的文档列表
     */
    public RetrievalResult retrieve(String query, String chatId) {
        logger.debug("开始检索 - query: {}, chatId: {}", query, chatId);
        
        try {
            // 1. 查询转换（包括上下文重写）
            String transformedQuery = transformQuery(query, chatId);
            logger.debug("查询转换完成 - 原始查询: {}, 转换后查询: {}", query, transformedQuery);
            
            // 2. 执行文档检索
            Query queryObj = Query.builder().text(transformedQuery).build();
            List<Document> documents = documentRetriever.retrieve(queryObj);
            logger.debug("检索完成 - 找到 {} 个文档", documents.size());
            
            return RetrievalResult.builder()
                    .originalQuery(query)
                    .transformedQuery(transformedQuery)
                    .documents(documents)
                    .success(true)
                    .build();
                    
        } catch (Exception e) {
            logger.error("检索失败 - query: {}, chatId: {}", query, chatId, e);
            return RetrievalResult.builder()
                    .originalQuery(query)
                    .transformedQuery(query)
                    .documents(List.of())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 仅执行查询转换，不进行实际检索
     * 用于调试和分析查询重写效果
     */
    public String transformQuery(String query, String chatId) {
        Query currentQuery = Query.builder().text(query).build();
        
        for (QueryTransformer transformer : queryTransformers) {
            try {
                currentQuery = transformer.transform(currentQuery);
                logger.debug("查询转换 - transformer: {}, 输入: {}, 输出: {}", 
                           transformer.getClass().getSimpleName(), query, currentQuery.text());
            } catch (Exception e) {
                logger.warn("查询转换失败 - transformer: {}, query: {}", 
                          transformer.getClass().getSimpleName(), currentQuery.text(), e);
            }
        }
        
        return currentQuery.text();
    }

    /**
     * 检索结果封装类
     */
    public static class RetrievalResult {
        private final String originalQuery;
        private final String transformedQuery;
        private final List<Document> documents;
        private final boolean success;
        private final String errorMessage;

        private RetrievalResult(Builder builder) {
            this.originalQuery = builder.originalQuery;
            this.transformedQuery = builder.transformedQuery;
            this.documents = builder.documents;
            this.success = builder.success;
            this.errorMessage = builder.errorMessage;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public String getOriginalQuery() { return originalQuery; }
        public String getTransformedQuery() { return transformedQuery; }
        public List<Document> getDocuments() { return documents; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }

        public static class Builder {
            private String originalQuery;
            private String transformedQuery;
            private List<Document> documents;
            private boolean success;
            private String errorMessage;

            public Builder originalQuery(String originalQuery) {
                this.originalQuery = originalQuery;
                return this;
            }

            public Builder transformedQuery(String transformedQuery) {
                this.transformedQuery = transformedQuery;
                return this;
            }

            public Builder documents(List<Document> documents) {
                this.documents = documents;
                return this;
            }

            public Builder success(boolean success) {
                this.success = success;
                return this;
            }

            public Builder errorMessage(String errorMessage) {
                this.errorMessage = errorMessage;
                return this;
            }

            public RetrievalResult build() {
                return new RetrievalResult(this);
            }
        }
    }
}