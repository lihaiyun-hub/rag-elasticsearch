package com.spring.ai.app.rag.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class JsonExtractor {

    private static final Logger logger = LoggerFactory.getLogger(JsonExtractor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "{\"operation\": \"unknown\", \"parameters\": {}}";
        }

        // 查找JSON代码块
        String jsonBlockStart = "```json";
        String jsonBlockEnd = "```";

        int startIndex = response.indexOf(jsonBlockStart);
        if (startIndex != -1) {
            startIndex += jsonBlockStart.length();
            int endIndex = response.indexOf(jsonBlockEnd, startIndex);
            if (endIndex != -1) {
                return response.substring(startIndex, endIndex).trim();
            }
        }

        // 如果没有找到代码块，尝试查找JSON对象
        int jsonStart = response.indexOf("{");
        int jsonEnd = response.lastIndexOf("}");

        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd + 1).trim();
        }

        // 如果都没找到，返回原始响应
        logger.warn("无法从响应中提取JSON - response: {}", response);
        return "{\"operation\": \"unknown\", \"parameters\": {}}";
    }

    /**
     * 解析模型返回的 JSON，提取 intent 与 parameters。
     * 若 intent 缺失或为空，直接抛出 IllegalArgumentException。
     */
    public static Map<String, Object> parseIntentAndParams(String jsonOutput) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = OBJECT_MAPPER.readValue(jsonOutput, Map.class);
            String intent = String.valueOf(root.getOrDefault("intent", "")).trim();
            if (intent == null || intent.isEmpty()) {
                throw new IllegalArgumentException("intent must not be null or empty");
            }
            Object paramsObj = root.get("parameters");
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (paramsObj instanceof Map) ? (Map<String, Object>) paramsObj : Map.of();
            return Map.of(
                    "intent", intent,
                    "params", params
            );
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse intent and parameters", e);
        }
    }

    /**
     * 从原始模型响应中，完成 JSON 提取与意图/参数解析，仅需一次方法调用。
     * 若 intent 缺失或为空，直接抛出 IllegalArgumentException。
     */
    public static Map<String, Object> parseIntentAndParamsFromRawResponse(String rawResponse) {
        String jsonOutput = extractJsonFromResponse(rawResponse);
        logger.info("大模型返回结果：{}", jsonOutput);
        return parseIntentAndParams(jsonOutput);
    }
}
