package com.spring.ai.app.rag.services;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.spring.ai.app.rag.model.Document;
import com.spring.ai.app.rag.vectorstore.ElasticsearchVectorStore;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

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
 * @author LHY
 * @date 2025-10-23
 */
@Service
public class KnowledgeBaseInitService implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseInitService.class);
    
    private final ElasticsearchVectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final ElasticsearchClient elasticsearchClient;
    
    @Value("${app.knowledge-base.init-data:true}")
    private boolean initData;
    
    @Value("${app.knowledge-base.clear-existing:false}")
    private boolean clearExisting;
    
    @Value("${app.knowledge-base.load-excel:true}")
    private boolean loadExcel;

    @Value("${spring.ai.retrieval.vector.index-name:${spring.ai.vectorstore.elasticsearch.index-name:customer_support_qa_ik}}")
    private String indexName;

    private static final String INIT_MARKER_ID = "__INIT_MARKER__";
    
    public KnowledgeBaseInitService(ElasticsearchVectorStore vectorStore,
                                    EmbeddingService embeddingService,
                                    ElasticsearchClient elasticsearchClient) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.elasticsearchClient = elasticsearchClient;
    }
    
    @Override
    public void run(String... args) {
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

            // 如果启用Excel数据加载，则导入Excel文件
            if (loadExcel) {
                logger.info("开始导入Excel文件数据...");
                List<Document> excelDocuments = loadExcelData();
                allDocuments.addAll(excelDocuments);
            }
            
            // 添加到向量数据库（分批处理）
            if (!allDocuments.isEmpty()) {
                addDocumentsInBatches(allDocuments);
                writeInitMarker();
                logger.info("知识库数据初始化完成，共添加 {} 条记录", allDocuments.size());
                try {
                    long totalCount = getIndexDocCount();
                    logger.info("导入完成后，索引总文档数：{}", totalCount);
                } catch (IOException e) {
                    logger.warn("统计索引文档数失败: {}", e.getMessage());
                }
            } else {
                logger.info("没有数据需要初始化");
            }
            
        } catch (Exception e) {
            logger.error("知识库数据初始化失败", e);
        }
    }
    
    /**
     * 检查是否已有数据：改为检测初始化标记文档是否存在
     */
    private boolean isDataAlreadyExists() throws IOException {
        // 检查索引是否存在
        var indexExists = elasticsearchClient.indices().exists(
            ExistsRequest.of(builder -> builder.index(indexName))
        ).value();
        
        if (!indexExists) {
            return false;
        }
        // 检查初始化标记是否存在
        try {
            GetResponse<Map> response = elasticsearchClient.get(g -> g.index(indexName).id(INIT_MARKER_ID), Map.class);
            return response.found();
        } catch (Exception e) {
            // 若 get 失败（如索引刚创建），视为未初始化
            logger.debug("检测初始化标记失败，视为未初始化: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 清除现有知识库数据
     */
    private void clearExistingKnowledgeBaseData() throws IOException {
        var indexExists = elasticsearchClient.indices().exists(
            ExistsRequest.of(builder -> builder.index(indexName))
        ).value();
        
        if (indexExists) {
            elasticsearchClient.indices().delete(builder -> builder.index(indexName));
            logger.info("已删除索引: {}", indexName);
        }
    }
    
    /**
     * 分批添加文档
     */
    private void addDocumentsInBatches(List<Document> documents) {
        int batchSize = 30;
        int totalDocuments = documents.size();
        
        for (int i = 0; i < totalDocuments; i += batchSize) {
            int end = Math.min(i + batchSize, totalDocuments);
            List<Document> batch = documents.subList(i, end);
            
            try {
                // 计算每个文档的向量嵌入
                List<List<Float>> embeddings = new ArrayList<>();
                for (Document doc : batch) {
                    List<Float> emb = embeddingService.embed(doc.getText());
                    embeddings.add(emb);
                }

                // 批量入库（文档 + 向量）
                vectorStore.addDocuments(batch, embeddings);
                logger.debug("成功添加第 {}/{} 批文档", (i + batchSize) / batchSize, (totalDocuments + batchSize - 1) / batchSize);
            } catch (Exception e) {
                logger.error("添加第 {}/{} 批文档时出错", (i + batchSize) / batchSize, (totalDocuments + batchSize - 1) / batchSize, e);
            }
        }
    }

    /**
     * 写入初始化标记文档（幂等）：固定 ID，避免重复导入
     */
    private void writeInitMarker() {
        try {
            Map<String, Object> source = new HashMap<>();
            source.put("text", "__INIT_MARKER__");
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("created_at", System.currentTimeMillis());
            metadata.put("type", "init_marker");
            source.put("metadata", metadata);
            elasticsearchClient.index(idx -> idx.index(indexName).id(INIT_MARKER_ID).document(source));
            logger.info("已写入初始化标记文档: {}", INIT_MARKER_ID);
        } catch (Exception e) {
            logger.warn("写入初始化标记失败: {}", e.getMessage());
        }
    }

    /**
     * 获取索引文档总数
     */
    private long getIndexDocCount() throws IOException {
        var indexExists = elasticsearchClient.indices().exists(
            ExistsRequest.of(builder -> builder.index(indexName))
        ).value();
        if (!indexExists) {
            return 0L;
        }
        CountResponse response = elasticsearchClient.count(
            CountRequest.of(builder -> builder.index(indexName))
        );
        return response.count();
    }
    

    

    
    /**
     * 加载Excel文件数据
     */
    private List<Document> loadExcelData() {
        // 加载思考过程补充结果Excel文件
        return  loadThinkingProcessData();
    }
    

    
    /**
     * 加载思考过程补充结果_20251025_095646.xlsx文件
     */
    private List<Document> loadThinkingProcessData() {
        List<Document> documents = new ArrayList<>();
        String fileName = "意图泛化语料.xlsx";
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
                        String historyQuestions = getCellValueAsString(row.getCell(0));
                        String latestQuestion = getCellValueAsString(row.getCell(1));
                        String cotThinking = getCellValueAsString(row.getCell(2));
                        String processingMethod = getCellValueAsString(row.getCell(3));
                        String answer = getCellValueAsString(row.getCell(5));
                        
                        // 按格式拼接：历史对话[user:xxx;user:yyy;]最新提问[zzz]
                        // 仅当最新提问非空时写入
                        if (latestQuestion != null && !latestQuestion.trim().isEmpty()) {
                            String augmented = buildAugmentedQueryFromExcel(historyQuestions, latestQuestion);
                            Document doc = createThinkingProcessDocument(augmented, answer, cotThinking, "", processingMethod, fileName, sheet.getSheetName());
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
     * 将Excel中的“历史提问”和“最新提问”按既定格式拼接为检索文本
     * 格式：历史对话[user:…;user:…;]最新提问[…]
     */
    private String buildAugmentedQueryFromExcel(String history, String latest) {
        String h = history == null ? "" : history.trim();
        String l = latest == null ? "" : latest.trim();
        if (!h.isEmpty()) {
            return "历史对话[" + h + "]最新提问[" + l + "]";
        }
        return "最新提问[" + l + "]";
    }
    
    /**
     * 获取单元格值为字符串
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toString();
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (IllegalStateException e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }
}
