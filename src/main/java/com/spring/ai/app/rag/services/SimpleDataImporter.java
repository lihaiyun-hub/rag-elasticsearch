package com.spring.ai.app.rag.services;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 简化的数据导入服务
 * 直接导入文本数据到Elasticsearch，不使用向量嵌入
 */
@Service
public class SimpleDataImporter {
    
    private static final Logger logger = LoggerFactory.getLogger(SimpleDataImporter.class);
    
    private final ElasticsearchClient elasticsearchClient;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    
    @Value("${app.thinking-process.excel-file-path:/d:/workspace/rag-elasticsearch/src/main/resources/rag/思考过程补充结果_20251025_095646.xlsx}")
    private String excelFilePath;
    
    @Value("${spring.ai.vectorstore.elasticsearch.index-name:customer_support_qa}")
    private String indexName;
    
    public SimpleDataImporter(ElasticsearchClient elasticsearchClient, EmbeddingModel embeddingModel) {
        this.elasticsearchClient = elasticsearchClient;
        this.embeddingModel = embeddingModel;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 导入思考过程数据（不使用向量嵌入）
     */
    public void importThinkingProcessDataSimple() {
        logger.info("🚀 开始简化导入思考过程数据...");
        
        try {
            // 确保索引存在
            ensureIndexExists();
            
            // 解析Excel并导入数据
            List<Map<String, Object>> documents = parseExcelToMaps();
            
            if (!documents.isEmpty()) {
                int successCount = 0;
                for (int i = 0; i < documents.size(); i++) {
                    try {
                        Map<String, Object> document = documents.get(i);
                        String documentId = "thinking_process_simple_" + (i + 1);
                        
                        // 生成embedding向量
                        String content = (String) document.get("content");
                        logger.info("🔍 开始为文档 {} 生成embedding，内容长度: {}", documentId, content != null ? content.length() : 0);
                        if (content != null && !content.trim().isEmpty()) {
                            try {
                                logger.info("📡 调用embedding模型...");
                                EmbeddingResponse embeddingResponse = embeddingModel.embedForResponse(List.of(content));
                                logger.info("📡 embedding模型响应: {}", embeddingResponse != null ? "成功" : "失败");
                                if (embeddingResponse != null && !embeddingResponse.getResults().isEmpty()) {
                                    float[] embedding = embeddingResponse.getResults().get(0).getOutput();
                                    document.put("embedding", embedding);
                                    logger.info("✅ 为文档 {} 生成了 {} 维embedding向量", documentId, embedding.length);
                                } else {
                                    logger.warn("⚠️ embedding响应为空或无结果");
                                }
                            } catch (Exception e) {
                                logger.error("⚠️ 为文档 {} 生成embedding失败: {}", documentId, e.getMessage(), e);
                            }
                        } else {
                            logger.warn("⚠️ 文档 {} 的content为空，跳过embedding生成", documentId);
                        }
                        
                        IndexRequest<Map<String, Object>> request = IndexRequest.of(builder -> builder
                            .index(indexName)
                            .id(documentId)
                            .document(document)
                        );
                        
                        IndexResponse response = elasticsearchClient.index(request);
                        
                        if (response.result().name().equals("CREATED") || response.result().name().equals("UPDATED")) {
                            successCount++;
                            logger.debug("✅ 成功导入文档: {}", documentId);
                        }
                        
                    } catch (Exception e) {
                        logger.warn("⚠️ 导入第 {} 个文档失败: {}", i + 1, e.getMessage());
                    }
                }
                
                logger.info("✅ 简化导入完成，成功导入 {} / {} 条数据", successCount, documents.size());
            } else {
                logger.warn("❌ 没有找到可导入的数据");
            }
            
        } catch (Exception e) {
            logger.error("❌ 简化导入思考过程数据失败", e);
            throw new RuntimeException("简化导入思考过程数据失败", e);
        }
    }
    
    /**
     * 确保索引存在
     */
    private void ensureIndexExists() throws IOException {
        ExistsRequest existsRequest = ExistsRequest.of(builder -> builder.index(indexName));
        
        if (!elasticsearchClient.indices().exists(existsRequest).value()) {
            logger.info("📋 创建索引: {}", indexName);
            
            // 创建索引映射
            String mappingJson = """
                {
                  "mappings": {
                    "properties": {
                      "content": {
                        "type": "text",
                        "analyzer": "ik_max_word",
                        "search_analyzer": "ik_smart"
                      },
                      "latest_question": {
                        "type": "text",
                        "analyzer": "ik_max_word",
                        "search_analyzer": "ik_smart"
                      },
                      "thinking_process": {
                        "type": "text",
                        "analyzer": "ik_max_word"
                      },
                      "intent": {
                        "type": "keyword"
                      },
                      "processing_method": {
                        "type": "keyword"
                      },
                      "source": {
                        "type": "keyword"
                      },
                      "import_date": {
                        "type": "date"
                      },
                      "response_data": {
                        "type": "object",
                        "enabled": false
                      },
                      "embedding": {
                        "type": "dense_vector",
                        "dims": 1024,
                        "index": true,
                        "similarity": "cosine"
                      }
                    }
                  }
                }
                """;
            
            CreateIndexRequest createRequest = CreateIndexRequest.of(builder -> builder
                .index(indexName)
                .withJson(new StringReader(mappingJson))
            );
            
            elasticsearchClient.indices().create(createRequest);
            logger.info("✅ 索引创建成功: {}", indexName);
        } else {
            logger.info("📋 索引已存在: {}", indexName);
        }
    }
    
    /**
     * 解析Excel文件为Map列表
     */
    private List<Map<String, Object>> parseExcelToMaps() throws IOException {
        List<Map<String, Object>> documents = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(excelFilePath);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheet("Sheet2");
            if (sheet == null) {
                logger.error("❌ 找不到工作表 'Sheet2'");
                return documents;
            }
            
            // 获取表头行
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                logger.error("❌ 找不到表头行");
                return documents;
            }
            
            // 解析表头
            Map<String, Integer> columnMap = parseHeaders(headerRow);
            logger.info("📋 解析到列: {}", columnMap.keySet());
            
            // 处理数据行
            int rowCount = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                try {
                    Map<String, Object> document = createDocumentMapFromRow(row, columnMap, i);
                    if (document != null) {
                        documents.add(document);
                        rowCount++;
                    }
                } catch (Exception e) {
                    logger.warn("⚠️ 处理第 {} 行数据时出错: {}", i + 1, e.getMessage());
                }
            }
            
            logger.info("✅ 成功解析 {} 行数据", rowCount);
        }
        
        return documents;
    }
    
    /**
     * 解析表头
     */
    private Map<String, Integer> parseHeaders(Row headerRow) {
        Map<String, Integer> columnMap = new HashMap<>();
        
        for (Cell cell : headerRow) {
            if (cell.getCellType() == CellType.STRING) {
                String header = cell.getStringCellValue().trim();
                columnMap.put(header, cell.getColumnIndex());
            }
        }
        
        return columnMap;
    }
    
    /**
     * 从Excel行创建Document Map
     */
    private Map<String, Object> createDocumentMapFromRow(Row row, Map<String, Integer> columnMap, int rowIndex) {
        try {
            // 获取各列数据，使用安全的方式处理null值
            String latestQuestion = getCellValue(row, columnMap.get("最新提问"));
            String thinkingProcess = getCellValue(row, columnMap.get("思考过程"));
            String userIntent = getCellValue(row, columnMap.get("意图"));
            String processingMethod = getCellValue(row, columnMap.get("处理"));
            String responseText = getCellValue(row, columnMap.get("回答"));

            logger.info("第{}行数据: 最新提问={}, 思考过程={}, 意图={}, 处理={}, 回答={}", 
                    rowIndex, 
                    latestQuestion != null ? latestQuestion.substring(0, Math.min(20, latestQuestion.length())) + "..." : "null",
                    thinkingProcess != null ? thinkingProcess.substring(0, Math.min(30, thinkingProcess.length())) + "..." : "null",
                    userIntent != null ? userIntent : "null",
                    processingMethod != null ? processingMethod : "null",
                    responseText != null ? responseText.substring(0, Math.min(20, responseText.length())) + "..." : "null");
            
            // 确保至少有一个非空字段才创建文档
            if ((latestQuestion == null || latestQuestion.trim().isEmpty()) &&
                (thinkingProcess == null || thinkingProcess.trim().isEmpty()) &&
                (userIntent == null || userIntent.trim().isEmpty()) &&
                (processingMethod == null || processingMethod.trim().isEmpty())) {
                logger.debug("跳过空行: {}", rowIndex + 1);
                return null;
            }
            
            // 构建文档内容
            StringBuilder contentBuilder = new StringBuilder();
            contentBuilder.append("用户提问: ").append(latestQuestion != null ? latestQuestion : "").append(" | ");
            contentBuilder.append("思考过程: ").append(thinkingProcess != null ? thinkingProcess : "").append(" | ");
            contentBuilder.append("用户意图: ").append(userIntent != null ? userIntent : "").append(" | ");
            contentBuilder.append("处理方式: ").append(processingMethod != null ? processingMethod : "");
            
            String content = contentBuilder.toString();
            
            // 解析响应数据
            Map<String, Object> responseData = parseResponseData(responseText);
            
            // 创建文档Map
            Map<String, Object> document = new HashMap<>();
            document.put("content", content);
            document.put("latest_question", latestQuestion != null ? latestQuestion : "");
            document.put("thinking_process", thinkingProcess != null ? thinkingProcess : "");
            document.put("intent", userIntent != null ? userIntent : "");
            document.put("processing_method", processingMethod != null ? processingMethod : "");
            document.put("source", "思考过程补充结果");
            document.put("import_date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            document.put("response_data", responseData);
            document.put("row_index", rowIndex);
            
            logger.debug("✅ 创建文档成功 (行 {}): {}", rowIndex + 1, latestQuestion);
            return document;
            
        } catch (Exception e) {
            logger.warn("⚠️ 创建文档时出错 (行 {}): {}", rowIndex + 1, e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取单元格值
     */
    private String getCellValue(Row row, Integer columnIndex) {
        if (columnIndex == null) return null;
        
        Cell cell = row.getCell(columnIndex);
        if (cell == null) return null;
        
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
            default:
                return null;
        }
    }
    
    /**
     * 解析响应数据JSON
     */
    private Map<String, Object> parseResponseData(String responseText) {
        Map<String, Object> responseData = new HashMap<>();
        
        if (responseText == null || responseText.trim().isEmpty()) {
            responseData.put("operation", "unknown");
            responseData.put("parameters", new HashMap<>());
            return responseData;
        }
        
        try {
            JsonNode jsonNode = objectMapper.readTree(responseText);
            
            if (jsonNode.has("operation")) {
                responseData.put("operation", jsonNode.get("operation").asText());
            } else {
                responseData.put("operation", "unknown");
            }
            
            if (jsonNode.has("parameters")) {
                responseData.put("parameters", objectMapper.convertValue(jsonNode.get("parameters"), Map.class));
            } else {
                responseData.put("parameters", new HashMap<>());
            }
            
        } catch (Exception e) {
            logger.warn("⚠️ 解析响应数据JSON失败: {}", e.getMessage());
            responseData.put("operation", "parse_error");
            responseData.put("parameters", new HashMap<>());
            responseData.put("raw_response", responseText);
        }
        
        return responseData;
    }
}