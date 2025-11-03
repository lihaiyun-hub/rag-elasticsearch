package com.spring.ai.app.rag.services;

import com.spring.ai.app.rag.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LargeLanguageModelService {
    private static final Logger logger = LoggerFactory.getLogger(LargeLanguageModelService.class);

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final double temperature;
    private final int maxTokens;

    // 简单的内存对话历史记录
    private final Map<String, List<Message>> chatHistory = new ConcurrentHashMap<>();

    public LargeLanguageModelService(
            @Value("${spring.ai.openai.chat.api-key:${spring.ai.openai.api-key:}}") String apiKey,
            @Value("${spring.ai.openai.chat.base-url:${spring.ai.openai.base-url:}}") String baseUrl,
            @Value("${spring.ai.openai.chat.options.model:Pro/deepseek-ai/DeepSeek-V3.1-Terminus}") String model,
            @Value("${spring.ai.openai.chat.options.temperature:0.7}") double temperature,
            @Value("${spring.ai.openai.chat.options.max-tokens:2048}") int maxTokens) {
        this.restTemplate = new RestTemplate();
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    /**
     * 执行聊天请求
     */
    public String chat(String prompt) {
        logger.debug("发送聊天请求 - prompt: {}", prompt);

        if (!org.springframework.util.StringUtils.hasText(apiKey)) {
            logger.warn("API Key未配置，使用本地降级输出(clarification)");
            return buildDefaultJsonResponse(prompt);
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(Map.of(
                "role", "user",
                "content", prompt
            )));
            requestBody.put("temperature", temperature);
            requestBody.put("max_tokens", maxTokens);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);   // 关键：加上 Bearer token

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String endpoint = baseUrl.endsWith("/") ? baseUrl + "v1/chat/completions" : baseUrl + "/v1/chat/completions";
            logger.info("大模型调用参数 - URL: {}, body: {}", endpoint, requestBody);

            Map<String, Object> response = restTemplate.postForObject(
                endpoint,
                entity,
                Map.class
            );

            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    return (String) message.get("content");
                }
            }

            logger.warn("无效的API响应格式 - response: {}", response);
            return "抱歉，我暂时无法理解您的问题。";

        } catch (Exception e) {
            logger.error("聊天请求失败", e);
            return "抱歉，系统暂时无法处理您的请求，请稍后再试。";
        }
    }

    /**
     * 执行携带 system+user 双消息的聊天请求
     */
    public String chatWithSystem(String systemPrompt, String userMessage) {
        logger.debug("发送聊天请求(system+user) - system: {}, user: {}", 
                systemPrompt.substring(0, Math.min(systemPrompt.length(), 100)),
                userMessage);

        if (!org.springframework.util.StringUtils.hasText(apiKey)) {
            logger.warn("API Key未配置，使用本地降级输出(system+user -> clarification)");
            return buildDefaultJsonResponse(userMessage);
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", systemPrompt
            ));
            messages.add(Map.of(
                "role", "user",
                "content", userMessage
            ));
            requestBody.put("messages", messages);
            requestBody.put("temperature", temperature);
            requestBody.put("max_tokens", maxTokens);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String endpoint = baseUrl.endsWith("/") ? baseUrl + "v1/chat/completions" : baseUrl + "/v1/chat/completions";
            logger.info("大模型调用参数(system+user) - URL: {}, body: {}", endpoint, requestBody);

            Map response = restTemplate.postForObject(
                endpoint,
                entity,
                Map.class
            );

            if (response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    return (String) message.get("content");
                }
            }

            logger.warn("无效的API响应格式 - response: {}", response);
            return "抱歉，我暂时无法理解您的问题。";

        } catch (Exception e) {
            logger.error("聊天请求失败(system+user)", e);
            return "抱歉，系统暂时无法处理您的请求，请稍后再试。";
        }
    }

    /**
     * ChatModel 风格：接受通用消息序列
     */
    public String chatMessages(List<Message> messages) {
        return chatMessages(messages, null, null);
    }

    /**
     * ChatModel 风格：接受通用消息序列并允许覆盖温度和最大tokens
     */
    public String chatMessages(List<Message> messages, Double temperatureOverride, Integer maxTokensOverride) {
        if (!org.springframework.util.StringUtils.hasText(apiKey)) {
            logger.warn("API Key未配置，使用本地降级输出(messages -> clarification)");
            String hint = null;
            if (messages != null) {
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Message m = messages.get(i);
                    if (m.getType() == Message.Type.USER) { hint = m.getContent(); break; }
                }
            }
            return buildDefaultJsonResponse(hint);
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            List<Map<String, Object>> payloadMessages = new ArrayList<>();
            for (Message m : messages) {
                payloadMessages.add(Map.of(
                    "role", toRoleString(m.getType()),
                    "content", m.getContent()
                ));
            }
            requestBody.put("messages", payloadMessages);
            requestBody.put("temperature", temperatureOverride != null ? temperatureOverride : temperature);
            requestBody.put("max_tokens", maxTokensOverride != null ? maxTokensOverride : maxTokens);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String endpoint = baseUrl.endsWith("/") ? baseUrl + "v1/chat/completions" : baseUrl + "/v1/chat/completions";
            logger.info("大模型调用参数(messages) - URL: {}, body: {}", endpoint, requestBody);

            Map<String, Object> response = restTemplate.postForObject(
                endpoint,
                entity,
                Map.class
            );

            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    return (String) message.get("content");
                }
            }

            logger.warn("无效的API响应格式 - response: {}", response);
            return "抱歉，我暂时无法理解您的问题。";

        } catch (Exception e) {
            logger.error("聊天请求失败(messages)", e);
            return "抱歉，系统暂时无法处理您的请求，请稍后再试。";
        }
    }

    /**
     * 当未配置API Key或模型不可用时，返回一个稳定可解析的JSON，
     * 使上层逻辑走“clarification”分支而不是抛出异常。
     */
    private String buildDefaultJsonResponse(String hint) {
        String safe = hint == null ? "" : hint.replace("\"", "'");
        return "{" +
                "\"operation\":\"clarification\"," +
                "\"parameters\":{" +
                    "\"hint\":\"" + safe + "\"," +
                    "\"dev_fallback\":true" +
                "}" +
               "}";
    }

    private String toRoleString(Message.Type type) {
        return switch (type) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }

    /**
     * 获取会话历史记录
     */
    public List<Message> getChatHistory(String chatId) {
        return chatHistory.getOrDefault(chatId, new ArrayList<>());
    }

    /**
     * 添加消息到会话历史记录
     */
    public void addMessage(String chatId, Message message) {
        chatHistory.computeIfAbsent(chatId, k -> new ArrayList<>()).add(message);
    }

    /**
     * 清除会话历史记录
     */
    public void clearHistory(String chatId) {
        chatHistory.remove(chatId);
    }
}
