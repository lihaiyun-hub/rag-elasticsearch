package com.spring.ai.app.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 按方案B导入：Excel（标准问题、相似问题、默认回答）→ 每个问题变体一条文档
 * 字段：
 * - question_text: 变体文本
 * - canonical_id: 标准问题的稳定ID
 * - is_canonical: 是否标准问题
 * - answer: 默认回答
 */
@SpringBootApplication
public class RagEsApplication {
    private static final Logger logger = LoggerFactory.getLogger(RagEsApplication.class);
    // 使用 DataFormatter 更稳健地读取各种单元格类型（文本/数字/日期/公式）

    public static void main(String[] args) {
        try {
            SpringApplication.run(RagEsApplication.class, args);
        } catch (Exception e) {
            logger.error("Application failed to start", e);
            throw e;
        }
    }









}
