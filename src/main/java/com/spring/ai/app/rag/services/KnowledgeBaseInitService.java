package com.spring.ai.app.rag.services;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.spring.ai.app.rag.model.KnowledgeRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库数据初始化服务
 * 在应用启动时加载测试数据到向量数据库
 * 
 * @author AI Assistant
 * @date 2025-10-23
 */
@Service
public class KnowledgeBaseInitService implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseInitService.class);
    
    private final VectorStore vectorStore;
    private final ElasticsearchClient elasticsearchClient;
    
    @Value("${app.knowledge-base.init-data:true}")
    private boolean initData;
    
    @Value("${app.knowledge-base.clear-existing:false}")
    private boolean clearExisting;
    
    @Value("${app.knowledge-base.load-excel:true}")
    private boolean loadExcel;
    
    @Value("${spring.ai.vectorstore.elasticsearch.index-name}")
    private String indexName;
    
    public KnowledgeBaseInitService(VectorStore vectorStore, ElasticsearchClient elasticsearchClient) {
        this.vectorStore = vectorStore;
        this.elasticsearchClient = elasticsearchClient;
    }
    
    @Override
    public void run(String... args) throws Exception {
        if (!initData) {
            logger.info("知识库数据初始化已禁用");
            return;
        }
        
        logger.info("开始初始化知识库数据...");
        
        try {
            // 如果需要清除现有数据
            if (clearExisting) {
                logger.info("清除现有数据...");
                clearExistingKnowledgeBaseData();
            } else {
                // 检查索引是否存在以及是否已有数据
                if (isDataAlreadyExists()) {
                    logger.info("知识库数据已存在，跳过初始化");
                    return;
                }
            }
            
            // 创建所有文档
            List<Document> allDocuments = new ArrayList<>();
            
            // 创建测试知识库记录
            List<Document> testDocuments = createTestKnowledgeRecords();
            allDocuments.addAll(testDocuments);
            
            // 如果启用Excel数据加载，则导入Excel文件
            if (loadExcel) {
                logger.info("开始导入Excel文件数据...");
                List<Document> excelDocuments = loadExcelData();
                allDocuments.addAll(excelDocuments);
                logger.info("Excel数据导入完成，共导入 {} 条记录", excelDocuments.size());
            }
            
            // 添加到向量数据库（分批处理）
            if (!allDocuments.isEmpty()) {
                addDocumentsInBatches(allDocuments);
                logger.info("知识库数据初始化完成，共添加 {} 条记录", allDocuments.size());
            } else {
                logger.info("没有数据需要初始化");
            }
            
        } catch (Exception e) {
            logger.error("知识库数据初始化失败", e);
        }
    }
    
    /**
     * 清除现有的知识库数据
     */
    private void clearExistingKnowledgeBaseData() {
        try {
            // 检查索引是否存在
            ExistsRequest existsRequest = ExistsRequest.of(e -> e.index(indexName));
            boolean indexExists = elasticsearchClient.indices().exists(existsRequest).value();
            
            if (!indexExists) {
                logger.info("索引 {} 不存在，无需清除数据", indexName);
                return;
            }
            
            // 删除索引中的所有数据（使用match_all查询）
            co.elastic.clients.elasticsearch.core.DeleteByQueryRequest deleteRequest = 
                co.elastic.clients.elasticsearch.core.DeleteByQueryRequest.of(d -> d
                    .index(indexName)
                    .query(q -> q.matchAll(m -> m))
                );
            
            co.elastic.clients.elasticsearch.core.DeleteByQueryResponse deleteResponse = 
                elasticsearchClient.deleteByQuery(deleteRequest);
            
            logger.info("已清除索引 {} 中的所有数据，共 {} 条记录", indexName, deleteResponse.deleted());
            
        } catch (Exception e) {
            logger.error("清除知识库数据失败", e);
            throw new RuntimeException("清除知识库数据失败", e);
        }
    }

    /**
     * 检查数据是否已存在
     */
    private boolean isDataAlreadyExists() {
        try {
            // 检查索引是否存在
            ExistsRequest existsRequest = ExistsRequest.of(e -> e.index(indexName));
            boolean indexExists = elasticsearchClient.indices().exists(existsRequest).value();
            
            if (!indexExists) {
                logger.info("索引 {} 不存在，需要初始化数据", indexName);
                return false;
            }
            
            // 检查索引中是否有任何数据
            CountRequest countRequest = CountRequest.of(c -> c
                    .index(indexName)
                    .query(q -> q.matchAll(m -> m))
            );
            
            CountResponse countResponse = elasticsearchClient.count(countRequest);
            long count = countResponse.count();
            
            if (count > 0) {
                logger.info("索引 {} 中已存在 {} 条记录", indexName, count);
                return true;
            } else {
                logger.info("索引 {} 存在但没有数据，需要初始化", indexName);
                return false;
            }
            
        } catch (Exception e) {
            logger.warn("检查数据存在性时出错，将继续初始化: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 创建测试知识库记录
     */
    private List<Document> createTestKnowledgeRecords() {
        List<Document> documents = new ArrayList<>();
        
        // 1. 借款相关的意图路由记录
        documents.add(createKnowledgeDocument(
                "借4000",
                KnowledgeRecord.ProcessingType.INTENT_ROUTING,
                "{\"operation\":\"modify_plan\",\"parameters\":{\"amount\":\"4000\",\"installments\":\"\",\"purpose\":\"\"}}",
                "用户在提问中表明要借款4000，因此提取用户意图为借款申请，触发操作为modify_plan，金额为4000，提问中没有分期数和借款用途，分期数为空，借款用途为空",
                "修改方案"
        ));
        
        documents.add(createKnowledgeDocument(
                "我想借5000块钱分12期",
                KnowledgeRecord.ProcessingType.INTENT_ROUTING,
                "{\"operation\":\"modify_plan\",\"parameters\":{\"amount\":\"5000\",\"installments\":\"12\",\"purpose\":\"\"}}",
                "用户明确表示要借款5000元，分12期还款，因此提取用户意图为借款申请，触发操作为modify_plan，金额为5000，期数为12，借款用途未提及为空",
                "修改方案"
        ));
        
        documents.add(createKnowledgeDocument(
                "申请3万元装修贷款",
                KnowledgeRecord.ProcessingType.INTENT_ROUTING,
                "{\"operation\":\"modify_plan\",\"parameters\":{\"amount\":\"30000\",\"installments\":\"\",\"purpose\":\"装修\"}}",
                "用户申请30000元装修贷款，因此提取用户意图为借款申请，触发操作为modify_plan，金额为30000，期数未提及为空，借款用途为装修",
                "修改方案"
        ));
        
        // 2. 查询相关的直接回答记录
        documents.add(createKnowledgeDocument(
                "利率是多少",
                KnowledgeRecord.ProcessingType.DIRECT_ANSWER,
                "我们的贷款年利率为3.85%-24%，具体利率根据您的信用状况和借款金额确定。您可以通过我们的利率计算器获取个性化利率报价。",
                "用户询问利率信息，这是常见的产品咨询问题，可以直接提供标准答案",
                ""
        ));
        
        documents.add(createKnowledgeDocument(
                "最高能借多少钱",
                KnowledgeRecord.ProcessingType.DIRECT_ANSWER,
                "根据您的信用状况，我们的个人信用贷款最高额度可达50万元。具体额度需要根据您的收入证明、信用记录等因素综合评估确定。",
                "用户询问最高借款额度，这是产品信息咨询，可以直接回答",
                ""
        ));
        
        documents.add(createKnowledgeDocument(
                "还款方式有哪些",
                KnowledgeRecord.ProcessingType.DIRECT_ANSWER,
                "我们提供以下还款方式：1. 等额本息：每月还款金额固定；2. 等额本金：每月还款本金固定，利息递减；3. 先息后本：前期只还利息，到期还本金。您可以根据自己的资金情况选择合适的还款方式。",
                "用户询问还款方式，这是产品功能咨询，可以直接提供详细说明",
                ""
        ));
        
        // 3. 更多意图路由记录
        documents.add(createKnowledgeDocument(
                "查看我的借款记录",
                KnowledgeRecord.ProcessingType.INTENT_ROUTING,
                "{\"operation\":\"query_records\",\"parameters\":{\"query_type\":\"借款记录\",\"time_range\":\"\"}}",
                "用户要查看借款记录，这是查询类操作，触发操作为query_records，查询类型为借款记录，时间范围未指定为空",
                "查询记录"
        ));
        
        documents.add(createKnowledgeDocument(
                "提前还款",
                KnowledgeRecord.ProcessingType.INTENT_ROUTING,
                "{\"operation\":\"early_repayment\",\"parameters\":{\"amount\":\"\",\"method\":\"\"}}",
                "用户提到提前还款，这是还款操作，触发操作为early_repayment，还款金额和还款方式未指定为空",
                "提前还款"
        ));
        
        logger.info("创建了 {} 条测试知识库记录", documents.size());
        return documents;
    }
    
    /**
     * 创建知识库文档
     */
    private Document createKnowledgeDocument(String query, KnowledgeRecord.ProcessingType processingType,
                                           String answer, String cotThinking, String intent) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("query", query);
        metadata.put("processing_type", processingType == KnowledgeRecord.ProcessingType.INTENT_ROUTING ? "意图路由" : "直接回答");
        metadata.put("answer", answer);
        metadata.put("cot_thinking", cotThinking);
        metadata.put("source", "knowledge_base");
        metadata.put("type", "knowledge_record");
        
        // 文档内容只使用query，用于向量检索
        String content = query;
        
        return new Document(content, metadata);
    }
    
    /**
     * 加载Excel文件数据
     */
    private List<Document> loadExcelData() {
        List<Document> documents = new ArrayList<>();
        
        // 加载问答知识库Excel文件
        documents.addAll(loadQAKnowledgeBase());
        
        // 加载思考过程补充结果Excel文件
        documents.addAll(loadThinkingProcessData());
        
        return documents;
    }
    
    /**
     * 加载问答知识库.xlsx文件
     */
    private List<Document> loadQAKnowledgeBase() {
        List<Document> documents = new ArrayList<>();
        String fileName = "问答知识库.xlsx";
        String resourcePath = "rag/" + fileName;
        
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                logger.warn("资源文件不存在: {}", resourcePath);
                return documents;
            }
            
            try (InputStream inputStream = resource.getInputStream();
                 XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
                
                Sheet sheet = workbook.getSheetAt(0);
                logger.info("开始处理文件: {}，工作表: {}", fileName, sheet.getSheetName());
                
                // 跳过标题行，从第二行开始
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    
                    try {
                        // Excel列结构：历史提问(0), 最新提问(1), 思考过程(2), 处理(3),意图(4), 回答(5)
                        String historyQuestion = getCellValueAsString(row.getCell(0));
                        String latestQuestion = getCellValueAsString(row.getCell(1));
                        String thinkingProcess = getCellValueAsString(row.getCell(2));
                        String processingMethod = getCellValueAsString(row.getCell(3));
                        String answer = getCellValueAsString(row.getCell(5));
                        
                        // 使用最新提问作为主要问题
                        if (latestQuestion != null && !latestQuestion.trim().isEmpty()) {
                            Document doc = createExcelDocument(latestQuestion, answer, thinkingProcess, processingMethod, fileName, sheet.getSheetName());
                            documents.add(doc);
                        }
                    } catch (Exception e) {
                        logger.warn("处理第 {} 行数据时出错: {}", i + 1, e.getMessage());
                    }
                }
                
                logger.info("从 {} 成功导入 {} 条记录", fileName, documents.size());
            }
            
        } catch (Exception e) {
            logger.error("读取文件 {} 时出错: {}", fileName, e.getMessage(), e);
        }
        
        return documents;
    }
    
    /**
     * 加载思考过程补充结果_20251025_095646.xlsx文件
     */
    private List<Document> loadThinkingProcessData() {
        List<Document> documents = new ArrayList<>();
        String fileName = "思考过程补充结果_20251025_095646.xlsx";
        String resourcePath = "rag/" + fileName;
        
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                logger.warn("资源文件不存在: {}", resourcePath);
                return documents;
            }
            
            try (InputStream inputStream = resource.getInputStream();
                 XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
                
                Sheet sheet = workbook.getSheetAt(0);
                logger.info("开始处理文件: {}，工作表: {}", fileName, sheet.getSheetName());
                
                // 跳过标题行，从第二行开始
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    
                    try {
                        // Excel列结构：历史提问(0), 最新提问(1), 思考过程(2), 处理(3), 回答(4)
                        String latestQuestion = getCellValueAsString(row.getCell(1));
                        String cotThinking = getCellValueAsString(row.getCell(2));
                        String processingMethod = getCellValueAsString(row.getCell(3));
                        String answer = getCellValueAsString(row.getCell(5));
                        
                        // 使用最新提问作为主要问题
                        if (latestQuestion != null && !latestQuestion.trim().isEmpty()) {
                            Document doc = createThinkingProcessDocument(latestQuestion, answer, cotThinking, "", processingMethod, fileName, sheet.getSheetName());
                            documents.add(doc);
                        }
                    } catch (Exception e) {
                        logger.warn("处理第 {} 行数据时出错: {}", i + 1, e.getMessage());
                    }
                }
                
                logger.info("从 {} 成功导入 {} 条记录", fileName, documents.size());
            }
            
        } catch (Exception e) {
            logger.error("读取文件 {} 时出错: {}", fileName, e.getMessage(), e);
        }
        
        return documents;
    }
    
    /**
     * 创建Excel文档（问答知识库）
     */
    private Document createExcelDocument(String question, String answer, String thinkingProcess, String processingMethod, String fileName, String sheetName) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("query", question);
        metadata.put("processing_type", processingMethod != null ? processingMethod : "直接回答");
        metadata.put("answer", answer != null ? answer : "");
        metadata.put("cot_thinking", thinkingProcess != null ? thinkingProcess : "");
        metadata.put("source", fileName);
        metadata.put("type", "excel_qa_data");
        metadata.put("sheet_name", sheetName);
        metadata.put("intent", "");
        
        // 文档内容只使用question，用于向量检索
        String content = question;
        
        return new Document(content, metadata);
    }
    
    /**
     * 创建思考过程文档
     */
    private Document createThinkingProcessDocument(String question, String answer, String cotThinking, String intent, String processingMethod, String fileName, String sheetName) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("query", question);
        metadata.put("processing_type", processingMethod != null ? processingMethod : "直接回答");
        metadata.put("answer", answer != null ? answer : "");
        metadata.put("cot_thinking", cotThinking != null ? cotThinking : "");
        metadata.put("source", fileName);
        metadata.put("type", "excel_thinking_data");
        metadata.put("sheet_name", sheetName);
        metadata.put("intent", intent != null ? intent : "");
        
        // 文档内容只使用question，用于向量检索
        String content = question;
        
        return new Document(content, metadata);
    }
    
    /**
     * 获取单元格值作为字符串
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            case BLANK:
                return "";
            default:
                return "";
        }
    }
    
    /**
     * 分批添加文档到向量存储，避免批量大小限制
     */
    private void addDocumentsInBatches(List<Document> documents) {
        final int BATCH_SIZE = 30; // 设置为30，小于最大限制32
        
        logger.info("开始分批添加文档，总数: {}，批量大小: {}", documents.size(), BATCH_SIZE);
        
        for (int i = 0; i < documents.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, documents.size());
            List<Document> batch = documents.subList(i, endIndex);
            
            try {
                vectorStore.add(batch);
                logger.info("成功添加第 {} 批文档，数量: {} (总进度: {}/{})", 
                    (i / BATCH_SIZE) + 1, batch.size(), endIndex, documents.size());
            } catch (Exception e) {
                logger.error("添加第 {} 批文档失败，跳过该批次: {}", (i / BATCH_SIZE) + 1, e.getMessage());
            }
        }
    }
}