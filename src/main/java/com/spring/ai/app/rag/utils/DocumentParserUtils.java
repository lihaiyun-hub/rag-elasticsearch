package com.spring.ai.app.rag.utils;

import com.spring.ai.app.rag.model.KnowledgeRecord;
import com.spring.ai.app.rag.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文档解析工具类
 * 提供统一的文档解析方法，避免代码重复
 * 
 * @author AI Assistant
 * @date 2025-01-27
 */
public class DocumentParserUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentParserUtils.class);
    
    /**
     * 解析文档内容为知识库记录
     * 
     * @param document Document对象
     * @return KnowledgeRecord 知识库记录
     */
    public static KnowledgeRecord parseDocumentToKnowledgeRecord(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }
        
        String content = document.getText();
        
        // 从文档metadata中获取信息，提供默认值
        String query = getMetadataAsString(document, "query", "");
        String processingType = getMetadataAsString(document, "processing_type", "直接回答");
        String answer = getMetadataAsString(document, "answer", content);
        String cotThinking = getMetadataAsString(document, "cot_thinking", "");
        String intent = getMetadataAsString(document, "intent", "");
        
        // 转换处理类型
        KnowledgeRecord.ProcessingType type = convertProcessingType(processingType);
        
        logger.debug("解析文档 - query: {}, processingType: {}", 
                    query, processingType);
        
        return KnowledgeRecord.builder()
                .query(query)
                .processingType(type)
                .answer(answer)
                .cotThinking(cotThinking)
                .intent(intent)
                .build();
    }
    
    /**
     * 从metadata中获取字符串值，提供默认值
     * 
     * @param document Document对象
     * @param key metadata键
     * @param defaultValue 默认值
     * @return 字符串值
     */
    private static String getMetadataAsString(Document document, String key, String defaultValue) {
        Object value = document.metadata().get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * 转换处理类型字符串为枚举
     * 
     * @param processingType 处理类型字符串
     * @return ProcessingType枚举
     */
    private static KnowledgeRecord.ProcessingType convertProcessingType(String processingType) {
        if ("意图路由".equals(processingType)) {
            return KnowledgeRecord.ProcessingType.INTENT_ROUTING;
        } else {
            return KnowledgeRecord.ProcessingType.DIRECT_ANSWER;
        }
    }

    /**
     * 校验知识库记录是否有效
     *
     * @param record 知识库记录
     * @return 是否有效（必须包含非空的query和answer）
     */
    public static boolean isValidKnowledgeRecord(KnowledgeRecord record) {
        if (record == null) {
            return false;
        }
        String query = record.getQuery();
        String answer = record.getAnswer();
        return query != null && !query.trim().isEmpty()
            && answer != null && !answer.trim().isEmpty();
    }
}
