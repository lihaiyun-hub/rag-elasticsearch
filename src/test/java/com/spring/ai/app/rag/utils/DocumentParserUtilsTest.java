package com.spring.ai.app.rag.utils;

import com.spring.ai.app.rag.model.KnowledgeRecord;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DocumentParserUtils工具类的单元测试
 * 验证优化后的parseDocumentToKnowledgeRecord方法是否正常工作
 */
public class DocumentParserUtilsTest {

    @Test
    public void testParseDocumentToKnowledgeRecord_WithCompleteMetadata() {
        // 准备测试数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("query", "利率是多少");
        metadata.put("processing_type", "直接回答");
        metadata.put("answer", "我们的贷款年利率为3.85%");
        metadata.put("cot_thinking", "用户询问利率信息，从知识库中查找相关信息");
        metadata.put("intent", "利率查询");

        Document document = new Document("测试内容", metadata);

        // 执行测试
        KnowledgeRecord result = DocumentParserUtils.parseDocumentToKnowledgeRecord(document);

        // 验证结果
        assertNotNull(result);
        assertEquals("利率是多少", result.getQuery());
        assertEquals(KnowledgeRecord.ProcessingType.DIRECT_ANSWER, result.getProcessingType());
        assertEquals("我们的贷款年利率为3.85%", result.getAnswer());
        assertEquals("用户询问利率信息，从知识库中查找相关信息", result.getCotThinking());
        assertEquals("利率查询", result.getIntent());
    }

    @Test
    public void testParseDocumentToKnowledgeRecord_WithScoreMetadata() {
        // 准备测试数据 - 使用score而不是distance
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("query", "还款方式有哪些");
        metadata.put("processing_type", "直接回答");
        metadata.put("answer", "支持等额本息和等额本金两种还款方式");

        Document document = new Document("测试内容", metadata);

        // 执行测试
        KnowledgeRecord result = DocumentParserUtils.parseDocumentToKnowledgeRecord(document);

        // 验证结果
        assertNotNull(result);
        assertEquals("还款方式有哪些", result.getQuery());
        assertEquals(KnowledgeRecord.ProcessingType.DIRECT_ANSWER, result.getProcessingType());
        assertEquals("支持等额本息和等额本金两种还款方式", result.getAnswer());
    }

    @Test
    public void testParseDocumentToKnowledgeRecord_WithSimilarityMetadata() {
        // 准备测试数据 - 使用similarity
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("query", "最高能借多少钱");
        metadata.put("processing_type", "意图路由");
        metadata.put("answer", "根据您的信用状况，最高可贷款50万元");

        Document document = new Document("测试内容", metadata);

        // 执行测试
        KnowledgeRecord result = DocumentParserUtils.parseDocumentToKnowledgeRecord(document);

        // 验证结果
        assertNotNull(result);
        assertEquals("最高能借多少钱", result.getQuery());
        assertEquals(KnowledgeRecord.ProcessingType.INTENT_ROUTING, result.getProcessingType());
        assertEquals("根据您的信用状况，最高可贷款50万元", result.getAnswer());
    }



    @Test
    public void testConvertProcessingType_ValidTypes() {
        // 测试通过完整的文档解析来验证转换逻辑
        Map<String, Object> metadata1 = new HashMap<>();
        metadata1.put("query", "测试查询1");
        metadata1.put("processing_type", "直接回答");
        metadata1.put("answer", "测试答案1");
        Document document1 = new Document("测试内容", metadata1);
        KnowledgeRecord result1 = DocumentParserUtils.parseDocumentToKnowledgeRecord(document1);
        assertEquals(KnowledgeRecord.ProcessingType.DIRECT_ANSWER, result1.getProcessingType());

        Map<String, Object> metadata2 = new HashMap<>();
        metadata2.put("query", "测试查询2");
        metadata2.put("processing_type", "意图路由");
        metadata2.put("answer", "测试答案2");
        Document document2 = new Document("测试内容", metadata2);
        KnowledgeRecord result2 = DocumentParserUtils.parseDocumentToKnowledgeRecord(document2);
        assertEquals(KnowledgeRecord.ProcessingType.INTENT_ROUTING, result2.getProcessingType());
    }

    @Test
    public void testConvertProcessingType_InvalidType() {
        // 测试无效类型默认为DIRECT_ANSWER
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("query", "测试查询");
        metadata.put("processing_type", "INVALID_TYPE");
        metadata.put("answer", "测试答案");
        Document document = new Document("测试内容", metadata);
        KnowledgeRecord result = DocumentParserUtils.parseDocumentToKnowledgeRecord(document);
        assertEquals(KnowledgeRecord.ProcessingType.DIRECT_ANSWER, result.getProcessingType());
    }

    @Test
    public void testIsValidKnowledgeRecord() {
        // 有效的记录
        KnowledgeRecord validRecord = KnowledgeRecord.builder()
                .query("测试查询")
                .answer("测试答案")
                .processingType(KnowledgeRecord.ProcessingType.DIRECT_ANSWER)
                .build();
        assertTrue(DocumentParserUtils.isValidKnowledgeRecord(validRecord));

        // 无效的记录 - 缺少query
        KnowledgeRecord invalidRecord1 = KnowledgeRecord.builder()
                .answer("测试答案")
                .build();
        assertFalse(DocumentParserUtils.isValidKnowledgeRecord(invalidRecord1));

        // 无效的记录 - 缺少answer
        KnowledgeRecord invalidRecord2 = KnowledgeRecord.builder()
                .query("测试查询")
                .build();
        assertFalse(DocumentParserUtils.isValidKnowledgeRecord(invalidRecord2));

        // null记录
        assertFalse(DocumentParserUtils.isValidKnowledgeRecord(null));
    }
}