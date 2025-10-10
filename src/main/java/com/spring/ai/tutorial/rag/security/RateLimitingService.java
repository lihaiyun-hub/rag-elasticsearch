package com.spring.ai.tutorial.rag.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 频率限制服务
 * 用于防止高频攻击和滥用
 */
@Service
public class RateLimitingService {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitingService.class);
    
    @Autowired
    private SecurityAuditLogger auditLogger;
    
    // 用户请求计数器
    private final Map<String, UserRequestInfo> userRequestCounts = new ConcurrentHashMap<>();
    
    // 默认配置
    private static final int DEFAULT_MAX_REQUESTS_PER_MINUTE = 10;
    private static final int DEFAULT_MAX_ATTACKS_PER_HOUR = 5;
    private static final int DEFAULT_BLOCK_DURATION_MINUTES = 15;
    
    // 配置参数
    private int maxRequestsPerMinute = DEFAULT_MAX_REQUESTS_PER_MINUTE;
    private int maxAttacksPerHour = DEFAULT_MAX_ATTACKS_PER_HOUR;
    private int blockDurationMinutes = DEFAULT_BLOCK_DURATION_MINUTES;
    
    /**
     * 检查用户是否被限制
     */
    public boolean isUserRateLimited(String userId) {
        if (userId == null) {
            return false;
        }
        
        UserRequestInfo info = userRequestCounts.get(userId);
        if (info == null) {
            return false;
        }
        
        // 检查是否被封锁
        if (info.isBlocked()) {
            if (LocalDateTime.now().isAfter(info.getBlockEndTime())) {
                // 封锁时间已过，解除封锁
                info.unblock();
                logger.info("用户 {} 已解除封锁", userId);
                return false;
            } else {
                logger.warn("用户 {} 仍处于封锁状态，剩余时间: {} 分钟", 
                           userId, getRemainingBlockTime(userId));
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 记录用户请求
     */
    public void recordUserRequest(String userId) {
        if (userId == null) {
            return;
        }
        
        UserRequestInfo info = userRequestCounts.computeIfAbsent(userId, k -> new UserRequestInfo());
        
        // 清理过期的请求记录
        info.cleanExpiredRequests();
        
        // 记录新请求
        info.recordRequest();
        
        // 检查频率限制
        if (info.getRequestCountLastMinute() > maxRequestsPerMinute) {
            logger.warn("用户 {} 超过频率限制: {} 次/分钟", userId, info.getRequestCountLastMinute());
            blockUser(userId, "频率限制");
        }
    }
    
    /**
     * 记录攻击行为
     */
    public void recordAttack(String userId, String attackType) {
        if (userId == null) {
            return;
        }
        
        UserRequestInfo info = userRequestCounts.computeIfAbsent(userId, k -> new UserRequestInfo());
        
        // 清理过期的攻击记录
        info.cleanExpiredAttacks();
        
        // 记录攻击
        info.recordAttack();
        
        int attackCount = info.getAttackCountLastHour();
        logger.warn("用户 {} 发生攻击行为: {}, 最近1小时攻击次数: {}", userId, attackType, attackCount);
        
        // 检查攻击频率
        if (attackCount >= maxAttacksPerHour) {
            logger.error("用户 {} 攻击频率过高，执行封锁: {} 次/小时", userId, attackCount);
            blockUser(userId, "攻击频率过高");
            auditLogger.logHighFrequencyAttack(userId, attackCount, "1小时");
        }
    }
    
    /**
     * 封锁用户
     */
    public void blockUser(String userId, String reason) {
        if (userId == null) {
            return;
        }
        
        UserRequestInfo info = userRequestCounts.computeIfAbsent(userId, k -> new UserRequestInfo());
        LocalDateTime blockEndTime = LocalDateTime.now().plusMinutes(blockDurationMinutes);
        
        info.block(blockEndTime);
        
        logger.warn("用户 {} 被封锁，原因: {}, 封锁结束时间: {}", userId, reason, blockEndTime);
        auditLogger.logSystemException(null, userId, "USER_BLOCKED", reason);
    }
    
    /**
     * 解除用户封锁
     */
    public void unblockUser(String userId) {
        if (userId == null) {
            return;
        }
        
        UserRequestInfo info = userRequestCounts.get(userId);
        if (info != null) {
            info.unblock();
            logger.info("用户 {} 已手动解除封锁", userId);
        }
    }
    
    /**
     * 获取剩余封锁时间（分钟）
     */
    public long getRemainingBlockTime(String userId) {
        if (userId == null) {
            return 0;
        }
        
        UserRequestInfo info = userRequestCounts.get(userId);
        if (info == null || !info.isBlocked()) {
            return 0;
        }
        
        return Duration.between(LocalDateTime.now(), info.getBlockEndTime()).toMinutes();
    }
    
    /**
     * 获取用户统计信息
     */
    public UserStatistics getUserStatistics(String userId) {
        if (userId == null) {
            return null;
        }
        
        UserRequestInfo info = userRequestCounts.get(userId);
        if (info == null) {
            return new UserStatistics(0, 0, false, 0);
        }
        
        return new UserStatistics(
            info.getRequestCountLastMinute(),
            info.getAttackCountLastHour(),
            info.isBlocked(),
            getRemainingBlockTime(userId)
        );
    }
    
    /**
     * 更新配置
     */
    public void updateConfiguration(int maxRequestsPerMinute, int maxAttacksPerHour, int blockDurationMinutes) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.maxAttacksPerHour = maxAttacksPerHour;
        this.blockDurationMinutes = blockDurationMinutes;
        
        logger.info("频率限制配置已更新 - 最大请求/分钟: {}, 最大攻击/小时: {}, 封锁时长: {} 分钟",
                   maxRequestsPerMinute, maxAttacksPerHour, blockDurationMinutes);
    }
    
    /**
     * 清理过期数据
     */
    public void cleanupExpiredData() {
        userRequestCounts.entrySet().removeIf(entry -> {
            UserRequestInfo info = entry.getValue();
            // 如果用户长时间没有活动，清理数据
            return !info.isBlocked() && 
                   info.getLastActivityTime().isBefore(LocalDateTime.now().minusHours(24));
        });
        
        logger.info("过期数据清理完成");
    }
    
    /**
     * 用户请求信息类
     */
    private static class UserRequestInfo {
        private final AtomicInteger requestCount = new AtomicInteger(0);
        private final AtomicInteger attackCount = new AtomicInteger(0);
        private LocalDateTime lastActivityTime = LocalDateTime.now();
        private LocalDateTime blockEndTime;
        
        /**
         * 记录请求
         */
        public synchronized void recordRequest() {
            requestCount.incrementAndGet();
            lastActivityTime = LocalDateTime.now();
        }
        
        /**
         * 记录攻击
         */
        public synchronized void recordAttack() {
            attackCount.incrementAndGet();
            lastActivityTime = LocalDateTime.now();
        }
        
        /**
         * 清理过期的请求记录
         */
        public synchronized void cleanExpiredRequests() {
            // 简单的清理策略：每分钟重置计数
            if (LocalDateTime.now().getMinute() != lastActivityTime.getMinute()) {
                requestCount.set(0);
            }
        }
        
        /**
         * 清理过期的攻击记录
         */
        public synchronized void cleanExpiredAttacks() {
            // 简单的清理策略：每小时重置计数
            if (LocalDateTime.now().getHour() != lastActivityTime.getHour()) {
                attackCount.set(0);
            }
        }
        
        /**
         * 封锁用户
         */
        public synchronized void block(LocalDateTime endTime) {
            this.blockEndTime = endTime;
        }
        
        /**
         * 解除封锁
         */
        public synchronized void unblock() {
            this.blockEndTime = null;
            this.attackCount.set(0);
        }
        
        /**
         * 是否被封锁
         */
        public boolean isBlocked() {
            return blockEndTime != null && LocalDateTime.now().isBefore(blockEndTime);
        }
        
        /**
         * 获取最近1分钟的请求数
         */
        public int getRequestCountLastMinute() {
            return requestCount.get();
        }
        
        /**
         * 获取最近1小时的攻击数
         */
        public int getAttackCountLastHour() {
            return attackCount.get();
        }
        
        /**
         * 获取最后活动时间
         */
        public LocalDateTime getLastActivityTime() {
            return lastActivityTime;
        }
        
        /**
         * 获取封锁结束时间
         */
        public LocalDateTime getBlockEndTime() {
            return blockEndTime;
        }
    }
    
    /**
     * 用户统计信息类
     */
    public static class UserStatistics {
        private final int requestsPerMinute;
        private final int attacksPerHour;
        private final boolean isBlocked;
        private final long remainingBlockTimeMinutes;
        
        public UserStatistics(int requestsPerMinute, int attacksPerHour, boolean isBlocked, long remainingBlockTimeMinutes) {
            this.requestsPerMinute = requestsPerMinute;
            this.attacksPerHour = attacksPerHour;
            this.isBlocked = isBlocked;
            this.remainingBlockTimeMinutes = remainingBlockTimeMinutes;
        }
        
        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }
        
        public int getAttacksPerHour() {
            return attacksPerHour;
        }
        
        public boolean isBlocked() {
            return isBlocked;
        }
        
        public long getRemainingBlockTimeMinutes() {
            return remainingBlockTimeMinutes;
        }
        
        @Override
        public String toString() {
            return String.format("UserStatistics{requests/min=%d, attacks/hour=%d, blocked=%s, remainingBlockTime=%dmin}",
                               requestsPerMinute, attacksPerHour, isBlocked, remainingBlockTimeMinutes);
        }
    }
}