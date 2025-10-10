package com.spring.ai.tutorial.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * Query Transformer配置类
 * 用于配置查询重写功能，提升RAG检索效果
 * 
 * @author LHY
 * @date 2025-09-22 16:08
 * @description 配置查询转换器，包括重写查询转换器
 */
@Configuration
public class QueryTransformerConfig {

    @Value("${spring.ai.rag.query-rewrite.enabled:true}")
    private boolean queryRewriteEnabled;

    @Value("${spring.ai.rag.query-rewrite.custom-prompt-resource:}")
    private Resource customPromptResource;

    /**
     * 配置重写查询转换器
     * 使用LLM来重写用户查询，提供更好的检索结果
     * 支持自定义prompt模板
     */
    @Bean
    @Qualifier("rewriteQueryTransformer")
    public QueryTransformer rewriteQueryTransformer(ChatClient.Builder chatClientBuilder) {
        if (!queryRewriteEnabled) {
            // 如果禁用，返回原查询的转换器
            return query -> query;
        }

        // 使用增强版上下文感知重写器
        return new com.spring.ai.tutorial.rag.transformer.ContextualRewriteQueryTransformer(chatClientBuilder, customPromptResource);
    }

    /**
     * 配置查询转换器列表
     * 可以在这里添加多个转换器，按顺序执行
     */
    @Bean
    @Qualifier("queryTransformers")
    public List<QueryTransformer> queryTransformers(
            @Qualifier("rewriteQueryTransformer") QueryTransformer rewriteQueryTransformer) {
        return List.of(rewriteQueryTransformer);
    }
}