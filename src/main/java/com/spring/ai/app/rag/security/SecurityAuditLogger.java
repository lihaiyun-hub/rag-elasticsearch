package com.spring.ai.app.rag.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 安全审计日志记录器
 * 用于记录和分析安全相关的事件
 */
@Component
public class SecurityAuditLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditLogger.class);
    private static final Logger securityLogger = LoggerFactory.getLogger("SECURITY_AUDIT");
    
    // 攻击统计
    private final Map<String, AtomicInteger> attackCounter = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> userAttackCounter = new ConcurrentHashMap<>();
    
    /**
     * 记录输入安全检查事件
     */
    public void logInputCheck(String chatId, String userId, boolean passed, double riskScore, String reason) {
        String eventType = passed ? "INPUT_CHECK_PASSED" : "INPUT_CHECK_FAILED";
        String logMessage = String.format("[%s] chatId=%s, userId=%s, riskScore=%.2f, reason=%s",
                                        eventType, chatId, userId, riskScore, reason);
        
        if (passed) {
            logger.info(logMessage);
        } else {
            securityLogger.warn(logMessage);
            incrementAttackCounter("input_injection");
            if (userId != null) {
                incrementUserAttackCounter(userId);
            }
        }
    }
    
    /**
     * 记录输出安全检查事件
     */
    public void logOutputCheck(String chatId, String userId, boolean passed, double riskScore, String reason) {
        String eventType = passed ? "OUTPUT_CHECK_PASSED" : "OUTPUT_CHECK_FAILED";
        String logMessage = String.format("[%s] chatId=%s, userId=%s, riskScore=%.2f, reason=%s",
                                        eventType, chatId, userId, riskScore, reason);
        
        if (passed) {
            logger.info(logMessage);
        } else {
            securityLogger.warn(logMessage);
            incrementAttackCounter("output_leak");
            if (userId != null) {
                incrementUserAttackCounter(userId);
            }
        }
    }
    
    /**
     * 记录系统异常事件
     */
    public void logSystemException(String chatId, String userId, String exceptionType, String message) {
        String logMessage = String.format("[SYSTEM_EXCEPTION] chatId=%s, userId=%s, type=%s, message=%s",
                                        chatId, userId, exceptionType, message);
        securityLogger.error(logMessage);
        incrementAttackCounter("system_exception");
    }
    
    /**
     * 记录配置变更事件
     */
    public void logConfigChange(String configName, String oldValue, String newValue, String operator) {
        String logMessage = String.format("[CONFIG_CHANGE] config=%s, operator=%s, time=%s",
                                        configName, operator, LocalDateTime.now());
        securityLogger.info(logMessage);
        logger.debug("Config change details - old: {}, new: {}", oldValue, newValue);
    }
    
    /**
     * 记录高频率攻击事件
     */
    public void logHighFrequencyAttack(String userId, int attackCount, String timeWindow) {
        String logMessage = String.format("[HIGH_FREQUENCY_ATTACK] userId=%s, attackCount=%d, timeWindow=%s, time=%s",
                                        userId, attackCount, timeWindow, LocalDateTime.now());
        securityLogger.warn(logMessage);
        incrementAttackCounter("high_frequency_attack");
    }
    
    /**
     * 记录系统启动事件
     */
    public void logSystemStartup() {
        securityLogger.info("[SYSTEM_STARTUP] Security system initialized at {}", LocalDateTime.now());
    }
    
    /**
     * 获取攻击统计信息
     */
    public Map<String, Integer> getAttackStatistics() {
        Map<String, Integer> stats = new ConcurrentHashMap<>();
        attackCounter.forEach((key, value) -> stats.put(key, value.get()));
        return stats;
    }
    
    /**
     * 获取用户攻击统计
     */
    public int getUserAttackCount(String userId) {
        AtomicInteger counter = userAttackCounter.get(userId);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * 重置攻击统计
     */
    public void resetAttackStatistics() {
        attackCounter.clear();
        userAttackCounter.clear();
        securityLogger.info("[STATISTICS_RESET] Attack statistics reset at {}", LocalDateTime.now());
    }
    
    /**
     * 检查用户是否达到攻击频率限制
     */
    public boolean isUserAttackRateLimited(String userId, int maxAttacks, String timeWindow) {
        int attackCount = getUserAttackCount(userId);
        if (attackCount >= maxAttacks) {
            logHighFrequencyAttack(userId, attackCount, timeWindow);
            return true;
        }
        return false;
    }
    
    /**
     * 增加攻击计数器
     */
    private void incrementAttackCounter(String attackType) {
        attackCounter.computeIfAbsent(attackType, k -> new AtomicInteger(0)).incrementAndGet();
    }
    
    /**
     * 增加用户攻击计数器
     */
    private void incrementUserAttackCounter(String userId) {
        userAttackCounter.computeIfAbsent(userId, k -> new AtomicInteger(0)).incrementAndGet();
    }
}