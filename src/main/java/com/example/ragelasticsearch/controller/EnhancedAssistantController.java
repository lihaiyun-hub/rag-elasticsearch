package com.example.ragelasticsearch.controller;

import com.spring.ai.app.rag.services.EnhancedCustomerSupportAssistant;
import com.spring.ai.app.rag.model.ChatRequest;
import com.spring.ai.app.rag.model.UserContext;
import com.spring.ai.app.rag.flow.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * 增强版助手控制器
 * 支持高阶方案D：统一意图槽位抽取 + 增强前置RAG召回
 */
@RestController
@RequestMapping("/api/v2/enhanced-assistant")
@CrossOrigin(origins = "*")
public class EnhancedAssistantController {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedAssistantController.class);

    @Autowired
    private EnhancedCustomerSupportAssistant enhancedAssistant;

    /**
     * 增强版聊天接口
     * 集成统一意图槽位抽取和增强RAG召回
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        try {
            logger.info("Enhanced chat request received: chatId={}, message={}",
                    request.getChatId(), request.getUserMessage());

            // 构建用户上下文
            UserContext userContext = buildUserContext(request);

            // 调用增强助手服务
            String response = enhancedAssistant.chat(
                    request.getChatId(),
                    request.getUserMessage(),
                    userContext
            );

            // 构建响应
            Map<String, Object> responsePayload = new HashMap<>();
            responsePayload.put("response", response);
            responsePayload.put("chatId", request.getChatId());
            responsePayload.put("timestamp", LocalDateTime.now());
            responsePayload.put("userName", request.getUserName());
            
            ChatResponse chatResponse = new ChatResponse(
                "text",
                responsePayload,
                "ENHANCED_RESPONSE",
                UUID.randomUUID().toString()
            );

            logger.info("Enhanced chat response generated: chatId={}, responseLength={}",
                    request.getChatId(), response.length());

            return ResponseEntity.ok(chatResponse);

        } catch (Exception e) {
            logger.error("Error processing enhanced chat request: chatId={}, error={}",
                    request.getChatId(), e.getMessage(), e);

            Map<String, Object> errorPayload = new HashMap<>();
            errorPayload.put("response", "抱歉，系统暂时无法处理您的请求，请稍后重试。");
            errorPayload.put("chatId", request.getChatId());
            errorPayload.put("timestamp", LocalDateTime.now());
            errorPayload.put("userName", request.getUserName());
            
            ChatResponse errorResponse = new ChatResponse(
                "text",
                errorPayload,
                "ERROR",
                UUID.randomUUID().toString()
            );

            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * 获取增强架构状态信息
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("architecture", "Enhanced RAG with Unified Intent-Slot Extraction");
        status.put("version", "2.0");
        status.put("features", Map.of(
                "unified_intent_slot_extraction", true,
                "enhanced_pre_rag_recall", true,
                "multi_way_retrieval", true,
                "intelligent_reranking", true,
                "query_expansion", true,
                "context_enhancement", true
        ));
        status.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(status);
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "EnhancedCustomerSupportAssistant");
        health.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(health);
    }

    /**
     * 构建用户上下文
     */
    private UserContext buildUserContext(ChatRequest request) {
        UserContext context = new UserContext();
        // 注意：UserContext没有userId和chatId字段，这些信息在ChatRequest中处理

        // 模拟用户信息（实际应用中应从数据库或用户服务获取）
        context.setUserName("张先生");
        context.setAvailableCredit(50000.0);
        context.setRecentRepaymentStatus("正常");
        context.setAuthorized(true); // 使用setAuthorized而不是setAuthorizationStatus
        context.setTermOptions(java.util.Arrays.asList(3, 6, 12, 24, 36));
        context.setLoanPurposes(java.util.Arrays.asList("装修", "旅游", "教育", "医疗", "购物", "其他"));

        return context;
    }

    /**
     * 获取架构对比信息
     */
    @GetMapping("/architecture-comparison")
    public ResponseEntity<Map<String, Object>> getArchitectureComparison() {
        Map<String, Object> comparison = new HashMap<>();

        // 原始架构
        Map<String, Object> originalArchitecture = new HashMap<>();
        originalArchitecture.put("intent_recognition", "Separated LLM-based");
        originalArchitecture.put("slot_extraction", "Embedded in system prompt");
        originalArchitecture.put("rag_retrieval", "Single-way retrieval");
        originalArchitecture.put("query_processing", "Basic contextual rewrite");
        originalArchitecture.put("reranking", "Simple reranker");

        // 增强架构（高阶方案D）
        Map<String, Object> enhancedArchitecture = new HashMap<>();
        enhancedArchitecture.put("intent_recognition", "Unified Intent-Slot Extraction Service");
        enhancedArchitecture.put("slot_extraction", "Integrated with intent recognition");
        enhancedArchitecture.put("rag_retrieval", "Multi-way retrieval (Vector + BM25 + Hybrid)");
        enhancedArchitecture.put("query_processing", "Enhanced query expansion and rewriting");
        enhancedArchitecture.put("reranking", "Intelligent reranking with fusion");
        enhancedArchitecture.put("context_enhancement", "Context-aware retrieval optimization");

        comparison.put("original", originalArchitecture);
        comparison.put("enhanced", enhancedArchitecture);
        comparison.put("improvements", Map.of(
                "unified_processing", "合并意图识别和槽位抽取，减少LLM调用次数",
                "enhanced_recall", "多路召回提升检索覆盖度和准确性",
                "intelligent_reranking", "基于意图和上下文的智能重排序",
                "query_expansion", "智能查询扩展提升召回效果",
                "context_optimization", "上下文感知的检索优化"
        ));

        return ResponseEntity.ok(comparison);
    }
}