package com.spring.ai.tutorial.rag.config;

import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author LHY
 * @date 2025-09-22 16:08
 * @description 内存配置类，配置对话内存和TOP_K参数
 */
@Configuration
public class MemoryConfig {
    
    @Value("${spring.ai.chat.memory.max-messages:10}")
    private int maxMessages;
    
    @Value("${spring.ai.chat.memory.top-k:100}")
    private int topK;

    @Bean
    public InMemoryChatMemoryRepository inMemoryChatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    @Bean
    public MessageWindowChatMemory messageWindowChatMemory(InMemoryChatMemoryRepository inMemoryChatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(inMemoryChatMemoryRepository)
                .maxMessages(maxMessages)
                .build();
    }

    @Bean
    public PromptChatMemoryAdvisor promptChatMemoryAdvisor(MessageWindowChatMemory messageWindowChatMemory) {
        return PromptChatMemoryAdvisor.builder(messageWindowChatMemory)
                .build();
    }
    
    /**
     * 获取TOP_K配置值
     */
    public int getTopK() {
        return topK;
    }
    
    /**
     * 获取最大消息数量配置值
     */
    public int getMaxMessages() {
        return maxMessages;
    }

}
