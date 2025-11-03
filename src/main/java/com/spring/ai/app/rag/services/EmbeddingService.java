package com.spring.ai.app.rag.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Service
public class EmbeddingService {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public EmbeddingService(
            @Value("${spring.ai.openai.embedding.api-key:${spring.ai.openai.api-key:}}") String apiKey,
            @Value("${spring.ai.openai.embedding.base-url:${spring.ai.openai.base-url:}}") String baseUrl,
            @Value("${spring.ai.openai.embedding.options.model:BAAI/bge-large-zh-v1.5}") String model) {
        this.restTemplate = new RestTemplate();
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    /**
     * 生成文本的向量嵌入
     */
    public List<Float> embed(String text) {
        logger.debug("生成文本嵌入 - text: {}", text);

        try {
            Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", text
            );
            String endpoint = baseUrl.endsWith("/") ? baseUrl + "v1/embeddings" : baseUrl + "/v1/embeddings";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isBlank()) {
                headers.setBearerAuth(apiKey);
            } else {
                logger.warn("未配置embedding API密钥，可能导致鉴权失败");
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> responseEntity = restTemplate.postForEntity(endpoint, entity, Map.class);
            Map<String, Object> response = responseEntity.getBody();

            if (response != null && response.containsKey("data")) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
                if (!data.isEmpty()) {
                    List<Number> embedding = (List<Number>) data.get(0).get("embedding");
                    List<Float> result = new ArrayList<>();
                    for (Number n : embedding) {
                        result.add(n.floatValue());
                    }
                    return result;
                }
            }

            logger.warn("无效的API响应格式 - response: {}", response);
            throw new RuntimeException("无效的API响应格式");

        } catch (Exception e) {
            logger.error("生成文本嵌入失败", e);
            throw new RuntimeException("生成文本嵌入失败", e);
        }
    }

    /**
     * 批量生成文本向量嵌入（一次请求计算多个输入）
     */
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("embedBatch 需要至少一个输入文本");
        }
        logger.debug("批量生成文本嵌入 - size: {}", texts.size());

        try {
            // 使用可变 Map，避免不可变 Map 对 List 入参支持不佳
            java.util.Map<String, Object> requestBody = new java.util.HashMap<>();
            requestBody.put("model", model);
            requestBody.put("input", texts);

            String endpoint = baseUrl.endsWith("/") ? baseUrl + "v1/embeddings" : baseUrl + "/v1/embeddings";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isBlank()) {
                headers.setBearerAuth(apiKey);
            } else {
                logger.warn("未配置embedding API密钥，可能导致鉴权失败");
            }

            HttpEntity<java.util.Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<java.util.Map> responseEntity = restTemplate.postForEntity(endpoint, entity, java.util.Map.class);
            java.util.Map<String, Object> response = responseEntity.getBody();

            if (response != null && response.containsKey("data")) {
                @SuppressWarnings("unchecked")
                java.util.List<java.util.Map<String, Object>> data = (java.util.List<java.util.Map<String, Object>>) response.get("data");
                java.util.List<java.util.List<Float>> result = new java.util.ArrayList<>();
                for (java.util.Map<String, Object> item : data) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Number> embedding = (java.util.List<Number>) item.get("embedding");
                    java.util.List<Float> vec = new java.util.ArrayList<>();
                    for (Number n : embedding) {
                        vec.add(n.floatValue());
                    }
                    result.add(vec);
                }
                // 防御性：如果返回条数与输入不一致，记录告警但仍返回现有结果
                if (result.size() != texts.size()) {
                    logger.warn("批量嵌入返回数量与输入不一致: inputs={} results={}", texts.size(), result.size());
                }
                return result;
            }

            logger.warn("无效的API响应格式 - response: {}", response);
            throw new RuntimeException("无效的API响应格式");

        } catch (Exception e) {
            logger.error("批量生成文本嵌入失败", e);
            throw new RuntimeException("批量生成文本嵌入失败", e);
        }
    }
}
