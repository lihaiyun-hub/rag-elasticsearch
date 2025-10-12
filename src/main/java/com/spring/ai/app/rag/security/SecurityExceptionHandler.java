package com.spring.ai.app.rag.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 安全异常处理器
 * 统一处理安全相关的异常
 */
@ControllerAdvice
public class SecurityExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityExceptionHandler.class);
    
    private final SecurityAuditLogger auditLogger;
    
    public SecurityExceptionHandler(SecurityAuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }
    
    /**
     * 处理提示注入异常
     */
    @ExceptionHandler(PromptInjectionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, Object>> handlePromptInjectionException(PromptInjectionException e) {
        logger.warn("处理提示注入异常: {}", e.getMessage());
        
        auditLogger.logInputCheck(e.getChatId(), e.getUserId(), false, e.getRiskScore(), e.getReason());
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "安全检测失败");
        response.put("message", "检测到异常请求，请使用正常的贷款咨询语言重新提问");
        response.put("path", e.getPath());
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理安全检查异常
     */
    @ExceptionHandler(SecurityCheckException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, Object>> handleSecurityCheckException(SecurityCheckException e) {
        logger.warn("处理安全检查异常: {}", e.getMessage());
        
        auditLogger.logOutputCheck(e.getChatId(), e.getUserId(), false, e.getRiskScore(), e.getReason());
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "响应安全检查失败");
        response.put("message", "系统检测到响应异常，请重新提问您的贷款相关问题");
        response.put("path", e.getPath());
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理频率限制异常
     */
    @ExceptionHandler(RateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ResponseEntity<Map<String, Object>> handleRateLimitException(RateLimitException e) {
        logger.warn("处理频率限制异常: {}", e.getMessage());
        
        auditLogger.logHighFrequencyAttack(e.getUserId(), e.getAttackCount(), e.getTimeWindow());
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        response.put("error", "请求过于频繁");
        response.put("message", "您的请求过于频繁，请稍后再试");
        response.put("retryAfter", e.getRetryAfter());
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
    }
    
    /**
     * 处理通用的安全异常
     */
    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<Map<String, Object>> handleSecurityException(SecurityException e) {
        logger.error("处理安全异常: {}", e.getMessage(), e);
        
        auditLogger.logSystemException(e.getChatId(), e.getUserId(), e.getClass().getSimpleName(), e.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", "系统安全异常");
        response.put("message", "系统处理出现问题，请稍后重试");
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * 自定义安全异常基类
     */
    public static class SecurityException extends RuntimeException {
        private final String chatId;
        private final String userId;
        
        public SecurityException(String message, String chatId, String userId) {
            super(message);
            this.chatId = chatId;
            this.userId = userId;
        }
        
        public String getChatId() {
            return chatId;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public String getPath() {
            return "/api/assistant/chat";
        }
    }
    
    /**
     * 提示注入异常
     */
    public static class PromptInjectionException extends SecurityException {
        private final double riskScore;
        private final String reason;
        
        public PromptInjectionException(String message, String chatId, String userId, double riskScore, String reason) {
            super(message, chatId, userId);
            this.riskScore = riskScore;
            this.reason = reason;
        }
        
        public double getRiskScore() {
            return riskScore;
        }
        
        public String getReason() {
            return reason;
        }
    }
    
    /**
     * 安全检查异常
     */
    public static class SecurityCheckException extends SecurityException {
        private final double riskScore;
        private final String reason;
        
        public SecurityCheckException(String message, String chatId, String userId, double riskScore, String reason) {
            super(message, chatId, userId);
            this.riskScore = riskScore;
            this.reason = reason;
        }
        
        public double getRiskScore() {
            return riskScore;
        }
        
        public String getReason() {
            return reason;
        }
    }
    
    /**
     * 频率限制异常
     */
    public static class RateLimitException extends SecurityException {
        private final int attackCount;
        private final String timeWindow;
        private final int retryAfter;
        
        public RateLimitException(String message, String userId, int attackCount, String timeWindow, int retryAfter) {
            super(message, null, userId);
            this.attackCount = attackCount;
            this.timeWindow = timeWindow;
            this.retryAfter = retryAfter;
        }
        
        public int getAttackCount() {
            return attackCount;
        }
        
        public String getTimeWindow() {
            return timeWindow;
        }
        
        public int getRetryAfter() {
            return retryAfter;
        }
    }
}