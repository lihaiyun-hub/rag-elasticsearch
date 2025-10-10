package com.spring.ai.tutorial.rag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;

@Configuration
@Profile("dev")
public class LangFuseConfig {

    @Value("${langfuse.host:http://localhost:3000}")
    private String langfuseHost;

    @Value("${langfuse.api.key:}")
    private String langfuseApiKey;

    @Value("${langfuse.public.key:}")
    private String langfusePublicKey;

    @PostConstruct
    public void init() {
        // 使用 Spring Boot Micrometer + OpenTelemetry 自动配置，避免混合多种 tracer 实现
        // 相关配置在 application-langfuse-dev.yml 的 management.otlp.tracing.* 中完成
        System.out.println("LangFuse 配置已初始化:");
        System.out.println("Host: " + langfuseHost);
        System.out.println("API Key: " + (langfuseApiKey.isEmpty() ? "未设置" : "已设置"));
        System.out.println("Public Key: " + (langfusePublicKey.isEmpty() ? "未设置" : "已设置"));
        System.out.println("OpenTelemetry 通过 Spring Boot 管理端点配置，目标: http://localhost:4318/v1/traces");
    }
}