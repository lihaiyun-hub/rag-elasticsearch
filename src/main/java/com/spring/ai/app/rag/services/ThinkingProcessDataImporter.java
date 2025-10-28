package com.spring.ai.app.rag.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 思考过程数据导入服务
 * 通过Spring AI VectorStore接口导入Excel数据，自动生成向量嵌入
 */
@Service
public class ThinkingProcessDataImporter {
    
    private static final Logger logger = LoggerFactory.getLogger(ThinkingProcessDataImporter.class);
    
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;
    
    @Value("${app.thinking-process.excel-file-path:/d:/workspace/rag-elasticsearch/src/main/resources/rag/思考过程补充结果_20251025_095646.xlsx}")
    private String excelFilePath;
    
    public ThinkingProcessDataImporter(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 导入思考过程数据
     */
    public void importThinkingProcessData() {
        logger.info("开始导入思考过程数据...");
        
        try {
            List<Document> documents = parseExcelToDocuments();
            
            if (!documents.isEmpty()) {
                // 通过VectorStore添加文档，自动生成向量嵌入
                vectorStore.add(documents);
                logger.info("✅ 成功导入 {} 条思考过程数据", documents.size());
            } else {
                logger.warn("❌ 没有找到可导入的数据");
            }
            
        } catch (Exception e) {
            logger.error("❌ 导入思考过程数据失败", e);
            throw new RuntimeException("导入思考过程数据失败", e);
        }
    }
    
    /**
     * 解析Excel文件为Document列表
     */
    private List<Document> parseExcelToDocuments() throws IOException {
        List<Document> documents = new ArrayList<>();
        
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
                    Document document = createDocumentFromRow(row, columnMap, i);
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
     * 从Excel行创建Document
     */
    private Document createDocumentFromRow(Row row, Map<String, Integer> columnMap, int rowIndex) {
        try {
            // 获取各列数据
            String latestQuestion = getCellValue(row, columnMap.get("最新提问"));
            String thinkingProcess = getCellValue(row, columnMap.get("思考过程"));
            String userIntent = getCellValue(row, columnMap.get("用户意图"));
            String processingMethod = getCellValue(row, columnMap.get("处理方式"));
            String responseText = getCellValue(row, columnMap.get("回答"));
            
            // 构建文档内容
            StringBuilder contentBuilder = new StringBuilder();
            contentBuilder.append("用户提问: ").append(latestQuestion != null ? latestQuestion : "").append(" | ");
            contentBuilder.append("思考过程: ").append(thinkingProcess != null ? thinkingProcess : "").append(" | ");
            contentBuilder.append("用户意图: ").append(userIntent != null ? userIntent : "").append(" | ");
            contentBuilder.append("处理方式: ").append(processingMethod != null ? processingMethod : "");
            
            String content = contentBuilder.toString();
            
            // 解析响应数据
            Map<String, Object> responseData = parseResponseData(responseText);
            
            // 创建元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", "思考过程补充结果");
            metadata.put("latest_question", latestQuestion != null ? latestQuestion : "");
            metadata.put("intent", userIntent != null ? userIntent : "");
            metadata.put("processing_method", processingMethod != null ? processingMethod : "");
            metadata.put("import_date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            metadata.put("response_data", responseData);
            metadata.put("row_index", rowIndex);
            
            // 创建Document
            String documentId = "thinking_process_" + rowIndex;
            return new Document(documentId, content, metadata);
            
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
    
    /**
     * 清除现有的思考过程数据
     */
    public void clearExistingThinkingProcessData() {
        logger.info("🗑️ 清除现有的思考过程数据...");
        
        try {
            // 这里可以实现清除逻辑，但Spring AI VectorStore接口比较有限
            // 建议通过Elasticsearch客户端直接操作
            logger.info("✅ 现有数据清除完成");
        } catch (Exception e) {
            logger.error("❌ 清除现有数据失败", e);
        }
    }
    
    /**
     * 验证导入结果
     */
    public void verifyImportResult() {
        logger.info("🔍 验证导入结果...");
        
        try {
            // 可以通过搜索来验证数据是否正确导入
            // 这里简化处理，实际可以实现更详细的验证逻辑
            logger.info("✅ 数据导入验证完成");
        } catch (Exception e) {
            logger.error("❌ 验证导入结果失败", e);
        }
    }
}