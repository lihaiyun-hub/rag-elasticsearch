package com.spring.ai.app.rag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * HTTP客户端配置
 * 优化连接超时和重试机制以提高DashScope API调用的稳定性
 */
@Configuration
public class HttpClientConfig {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientConfig.class);

    /**
     * 配置RestTemplate with optimized timeouts
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .build();
        
        logger.info("RestTemplate配置完成 - 连接超时: 10秒");
        
        return restTemplate;
    }
}