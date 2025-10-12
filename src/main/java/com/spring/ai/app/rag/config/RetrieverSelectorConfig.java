package com.spring.ai.app.rag.config;

import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 检索方式选择器配置类
 * 支持通过配置切换不同的检索方式：hybrid、vector、bm25
 * 
 * @author LHY
 * @date 2025-09-22 16:08
 * @description 根据配置动态选择DocumentRetriever实现
 */
@Configuration
public class RetrieverSelectorConfig {

    @Value("${spring.ai.rag.retriever.type:hybrid}")
    private String retrieverType;

    /**
     * 根据配置选择主DocumentRetriever
     * 支持三种检索方式：
     * - hybrid: 混合检索（默认）
     * - vector: 向量检索
     * - bm25: BM25检索
     */
    @Bean
    @Primary
    public DocumentRetriever selectedDocumentRetriever(
            @Qualifier("hybridDocumentRetriever") DocumentRetriever hybridDocumentRetriever,
            @Qualifier("vectorStoreDocumentRetriever") DocumentRetriever vectorStoreDocumentRetriever,
            @Qualifier("BM25DocumentRetriever") DocumentRetriever bm25DocumentRetriever) {
        
        switch (retrieverType.toLowerCase()) {
            case "vector":
                return vectorStoreDocumentRetriever;
            case "bm25":
                return bm25DocumentRetriever;
            case "hybrid":
            default:
                return hybridDocumentRetriever;
        }
    }
}