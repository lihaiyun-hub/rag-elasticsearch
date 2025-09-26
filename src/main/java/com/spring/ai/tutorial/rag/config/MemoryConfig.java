package com.spring.ai.tutorial.rag.config;

import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author LHY
 * @date 2025-09-22 16:08
 * @description
 */
@Configuration
public class MemoryConfig {
    private final int MAX_MESSAGES = 10;


    @Bean
    public InMemoryChatMemoryRepository inMemoryChatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }


    @Bean
    public MessageWindowChatMemory messageWindowChatMemory(InMemoryChatMemoryRepository inMemoryChatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(inMemoryChatMemoryRepository)
                .maxMessages(MAX_MESSAGES)
                .build();
    }

    @Bean
    public PromptChatMemoryAdvisor promptChatMemoryAdvisor(MessageWindowChatMemory messageWindowChatMemory) {
        return PromptChatMemoryAdvisor.builder(messageWindowChatMemory)
                .build();
    }


}
