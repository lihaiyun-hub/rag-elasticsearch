package com.spring.ai.app.rag.config;

import com.spring.ai.app.rag.services.LLMService;
import com.spring.ai.app.rag.services.RetrievalService;
import com.spring.ai.app.rag.services.IntentExtractionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * RAG服务配置类
 * 提供RAG相关服务的Bean配置和基础属性配置
 * 
 * @author LHY
 * @date 2025-01-20
 */
@Configuration
public class RAGStrategyConfig {

    /**
     * 创建检索服务Bean
     */
    @Bean
    public RetrievalService retrievalService(DocumentRetriever documentRetriever,
                                           List<QueryTransformer> queryTransformers) {
        return new RetrievalService(documentRetriever, queryTransformers);
    }

    /**
     * 创建LLM服务Bean
     */
    @Bean
    public LLMService llmService(ChatClient.Builder chatClientBuilder,
                                ChatMemory chatMemory,
                                PromptChatMemoryAdvisor promptChatMemoryAdvisor,
                                @Qualifier("systemPrompt") Resource systemPromptResource) {
        return new LLMService(systemPromptResource, chatClientBuilder, chatMemory, promptChatMemoryAdvisor);
    }

    /**
     * 创建意图提取服务Bean
     */
    @Bean
    public IntentExtractionService intentExtractionService(ChatClient.Builder chatClientBuilder, 
                                                          @Qualifier("intentExtractionPrompt") Resource intentExtractionPrompt) {
        return new IntentExtractionService(chatClientBuilder, intentExtractionPrompt);
    }




}