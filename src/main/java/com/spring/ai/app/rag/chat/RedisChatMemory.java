package com.spring.ai.app.rag.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.ai.app.rag.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ListOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 基于 Redis 的聊天记忆实现
 * 每个会话(chatId)对应一个 Redis List，元素为消息的JSON字符串。
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.chat.memory", name = "backend", havingValue = "redis")
public class RedisChatMemory implements ChatMemory {

    private static final Logger logger = LoggerFactory.getLogger(RedisChatMemory.class);
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final int maxMessages;
    private final int ttlSeconds;

    public RedisChatMemory(StringRedisTemplate redisTemplate,
                           ObjectMapper objectMapper,
                           @Value("${spring.ai.chat.memory.max-messages:20}") int defaultMaxMessages,
                           @Value("${spring.ai.chat.memory.max-rounds:0}") int defaultMaxRounds,
                           @Value("${spring.ai.chat.memory.ttl-seconds:86400}") int ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttlSeconds = Math.max(ttlSeconds, 0);
        if (defaultMaxRounds > 0) {
            this.maxMessages = defaultMaxRounds * 2;
        } else {
            this.maxMessages = Math.max(defaultMaxMessages, 0);
        }
    }

    @Override
    public List<Message> get(String chatId) {
        try {
            String key = key(chatId);
            ListOperations<String, String> ops = redisTemplate.opsForList();
            List<String> raw = ops.range(key, 0, -1);
            if (raw == null || raw.isEmpty()) {
                return Collections.emptyList();
            }
            List<Message> messages = new ArrayList<>(raw.size());
            for (String js : raw) {
                try {
                    messages.add(fromJson(js));
                } catch (Exception ex) {
                    logger.debug("跳过无法解析的消息JSON: {}", ex.getMessage());
                }
            }
            return Collections.unmodifiableList(messages);
        } catch (Exception e) {
            logger.warn("Redis聊天记忆读取失败，chatId={}，返回空列表: {}", chatId, e.toString());
            return Collections.emptyList();
        }
    }

    @Override
    public void add(String chatId, Message message) {
        try {
            String key = key(chatId);
            ListOperations<String, String> ops = redisTemplate.opsForList();
            ops.rightPush(key, toJson(message));

            if (ttlSeconds > 0) {
                redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
            }

            int limit = Math.max(maxMessages, 0);
            if (limit == 0) {
                // 不保留历史，直接清空
                redisTemplate.delete(key);
                return;
            }
            Long size = ops.size(key);
            if (size != null && size > limit) {
                int start = (int) (size - limit);
                int end = (int) (size - 1);
                ops.trim(key, start, end);
            }
        } catch (Exception e) {
            logger.warn("Redis聊天记忆写入失败，chatId={}: {}", chatId, e.toString());
        }
    }

    @Override
    public void clear(String chatId) {
        try {
            redisTemplate.delete(key(chatId));
        } catch (Exception e) {
            logger.warn("Redis聊天记忆清理失败，chatId={}: {}", chatId, e.toString());
        }
    }

    private String key(String chatId) {
        return "chat:mem:" + chatId; // chatId 可包含租户前缀，例如 t1:abcd
    }

    private String toJson(Message m) throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("type", m.getType().name());
        payload.put("content", m.getContent());
        payload.put("timestamp", (m.getTimestamp() != null ? m.getTimestamp() : LocalDateTime.now()).format(TS_FMT));
        if (m.getMetadata() != null) {
            payload.put("metadata", m.getMetadata());
        }
        return objectMapper.writeValueAsString(payload);
    }

    private Message fromJson(String js) throws Exception {
        Map<String, Object> map = objectMapper.readValue(js, new TypeReference<Map<String, Object>>() {});
        Message.Type type = Message.Type.valueOf(String.valueOf(map.getOrDefault("type", "USER")));
        String content = (String) map.getOrDefault("content", "");
        @SuppressWarnings("unchecked") Map<String, Object> metadata = (Map<String, Object>) map.get("metadata");
        String tsStr = (String) map.get("timestamp");
        LocalDateTime ts = null;
        if (tsStr != null && !tsStr.isEmpty()) {
            try { ts = LocalDateTime.parse(tsStr, TS_FMT); } catch (Exception ignore) {}
        }
        Message.Builder b = Message.builder().type(type).content(content);
        if (metadata != null) { b.metadata(metadata); }
        if (ts != null) { b.timestamp(ts); }
        return b.build();
    }
}
