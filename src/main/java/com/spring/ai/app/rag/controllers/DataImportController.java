package com.spring.ai.app.rag.controllers;

import com.spring.ai.app.rag.services.ThinkingProcessDataImporter;
import com.spring.ai.app.rag.services.SimpleDataImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据导入控制器
 * 提供API接口来导入思考过程数据
 */
@RestController
@RequestMapping("/api/data-import")
public class DataImportController {
    
    private static final Logger logger = LoggerFactory.getLogger(DataImportController.class);
    
    private final ThinkingProcessDataImporter dataImporter;
    private final SimpleDataImporter simpleDataImporter;
    
    public DataImportController(ThinkingProcessDataImporter dataImporter, SimpleDataImporter simpleDataImporter) {
        this.dataImporter = dataImporter;
        this.simpleDataImporter = simpleDataImporter;
    }
    
    /**
     * 导入思考过程数据
     */
    @PostMapping("/thinking-process")
    public ResponseEntity<Map<String, Object>> importThinkingProcessData() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("🚀 开始导入思考过程数据...");
            
            // 执行数据导入
            dataImporter.importThinkingProcessData();
            
            // 验证导入结果
            dataImporter.verifyImportResult();
            
            response.put("success", true);
            response.put("message", "思考过程数据导入成功");
            response.put("timestamp", System.currentTimeMillis());
            
            logger.info("✅ 思考过程数据导入完成");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ 导入思考过程数据失败", e);
            
            response.put("success", false);
            response.put("message", "导入失败: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 清除现有数据并重新导入
     */
    @PostMapping("/thinking-process/reset")
    public ResponseEntity<Map<String, Object>> resetAndImportThinkingProcessData() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("🔄 开始重置并导入思考过程数据...");
            
            // 清除现有数据
            dataImporter.clearExistingThinkingProcessData();
            
            // 重新导入数据
            dataImporter.importThinkingProcessData();
            
            // 验证导入结果
            dataImporter.verifyImportResult();
            
            response.put("success", true);
            response.put("message", "思考过程数据重置并导入成功");
            response.put("timestamp", System.currentTimeMillis());
            
            logger.info("✅ 思考过程数据重置并导入完成");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ 重置并导入思考过程数据失败", e);
            
            response.put("success", false);
            response.put("message", "重置并导入失败: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 简化导入思考过程数据（不使用向量嵌入）
     */
    @PostMapping("/thinking-process/simple")
    public ResponseEntity<Map<String, Object>> importThinkingProcessDataSimple() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("🚀 开始简化导入思考过程数据...");
            
            // 执行简化数据导入
            simpleDataImporter.importThinkingProcessDataSimple();
            
            response.put("success", true);
            response.put("message", "思考过程数据简化导入成功");
            response.put("timestamp", System.currentTimeMillis());
            
            logger.info("✅ 思考过程数据简化导入完成");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ 简化导入思考过程数据失败", e);
            
            response.put("success", false);
            response.put("message", "简化导入失败: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 获取导入状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getImportStatus() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 这里可以实现更详细的状态检查
            response.put("success", true);
            response.put("message", "数据导入服务运行正常");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ 获取导入状态失败", e);
            
            response.put("success", false);
            response.put("message", "获取状态失败: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(response);
        }
    }
}