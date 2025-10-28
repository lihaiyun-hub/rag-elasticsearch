package com.spring.ai.app.rag.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class EmbeddingTestController {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingTestController.class);
    
    private final EmbeddingModel embeddingModel;

    public EmbeddingTestController(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @PostMapping("/embedding")
    public Map<String, Object> testEmbedding(@RequestBody Map<String, String> request) {
        String text = request.getOrDefault("text", "测试文本");
        
        try {
            logger.info("🔍 开始测试embedding，文本: {}", text);
            logger.info("🔍 EmbeddingModel实例: {}", embeddingModel != null ? embeddingModel.getClass().getName() : "null");
            
            if (embeddingModel == null) {
                return Map.of(
                    "success", false,
                    "error", "EmbeddingModel未注入"
                );
            }
            
            EmbeddingResponse embeddingResponse = embeddingModel.embedForResponse(List.of(text));
            logger.info("📡 embedding模型响应: {}", embeddingResponse != null ? "成功" : "失败");
            
            if (embeddingResponse != null && !embeddingResponse.getResults().isEmpty()) {
                float[] embedding = embeddingResponse.getResults().get(0).getOutput();
                logger.info("✅ 生成了 {} 维embedding向量", embedding.length);
                
                return Map.of(
                    "success", true,
                    "dimensions", embedding.length,
                    "text", text,
                    "embedding_preview", java.util.Arrays.copyOf(embedding, Math.min(5, embedding.length))
                );
            } else {
                logger.warn("⚠️ embedding响应为空或无结果");
                return Map.of(
                    "success", false,
                    "error", "embedding响应为空或无结果"
                );
            }
        } catch (Exception e) {
            logger.error("⚠️ embedding生成失败: {}", e.getMessage(), e);
            return Map.of(
                "success", false,
                "error", e.getMessage(),
                "stackTrace", java.util.Arrays.toString(e.getStackTrace())
            );
        }
    }
}