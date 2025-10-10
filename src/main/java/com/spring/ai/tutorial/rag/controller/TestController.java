package com.spring.ai.tutorial.rag.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @Value("${langfuse.host:}")
    private String langfuseHost;

    @Value("${langfuse.api.key:}")
    private String langfuseApiKey;

    @Value("${langfuse.public.key:}")
    private String langfusePublicKey;

    @GetMapping("/langfuse")
    public String testLangFuse() {
        return "LangFuse Configuration:\n" +
               "Host: " + langfuseHost + "\n" +
               "API Key: " + (langfuseApiKey != null && !langfuseApiKey.isEmpty() ? "已配置" : "未配置") + "\n" +
               "Public Key: " + (langfusePublicKey != null && !langfusePublicKey.isEmpty() ? "已配置" : "未配置");
    }

    @GetMapping("/health")
    public String health() {
        return "Application is running with LangFuse integration!";
    }
}