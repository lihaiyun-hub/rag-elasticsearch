package com.spring.ai.tutorial.rag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * @author LHY
 * @date 2025-09-23 11:30
 * @description
 */
@Configuration
public class PromptConfig {
    @Value("classpath:/prompts/system_prompt.st")
    private Resource systemPromptResource;

    @Bean
    public Resource systemPrompt() throws IOException {
        return systemPromptResource;
    }

}
