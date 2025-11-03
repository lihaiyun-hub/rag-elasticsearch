package com.spring.ai.app.rag.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonExtractor {

    private static final Logger logger = LoggerFactory.getLogger(JsonExtractor.class);

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
}
