package com.spring.ai.app.rag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * @author LHY
 * @date 2025-09-23 11:30
 * @description
 */
@Configuration
public class PromptConfig {
    @Value("classpath:/prompts/system-prompt.st")
    private Resource systemPromptResource;
    
    @Value("classpath:/prompts/intent-extraction-prompt.st")
    private Resource intentExtractionPromptResource;
    
    @Value("classpath:/prompts/contextual-rewrite-prompt.st")
    private Resource customPromptResourceValue;

    @Bean
    public Resource systemPrompt() throws IOException {
        return systemPromptResource;
    }
    
    @Bean
    public Resource intentExtractionPrompt() throws IOException {
        return intentExtractionPromptResource;
    }
    
    @Bean
    public Resource customPromptResource() throws IOException {
        return customPromptResourceValue;
    }

}
