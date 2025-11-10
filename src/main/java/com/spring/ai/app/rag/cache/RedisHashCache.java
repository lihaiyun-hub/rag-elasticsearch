package com.spring.ai.app.rag.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * 通用 Redis Hash 工具类：支持字符串字段与 JSON 字段读写。
 * - hPutAll/hEntries：批量写入和读取 Hash 字段
 * - hPutJson/hGetJson：将对象作为 JSON 写入/读取指定字段
 * - expire/delete：设置过期或删除整个键
 */
@Component
public class RedisHashCache {
    private static final Logger logger = LoggerFactory.getLogger(RedisHashCache.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final int ttlSeconds;

    public RedisHashCache(StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper,
                          @Value("${spring.ai.cache.ttl-seconds:7200}") int ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttlSeconds = Math.max(ttlSeconds, 0);
    }

    public void hPutAll(String key, Map<String, String> fields) {
        if (key == null || key.isBlank() || fields == null || fields.isEmpty()) return;
        try {
            HashOperations<String, String, String> ops = redisTemplate.opsForHash();
            ops.putAll(key, fields);
            applyExpire(key);
        } catch (Exception e) {
            logger.warn("hPutAll failed key={}: {}", key, e.toString());
        }
    }

    public Map<String, String> hEntries(String key) {
        if (key == null || key.isBlank()) return Map.of();
        try {
            return redisTemplate.<String, String>opsForHash().entries(key);
        } catch (Exception e) {
            logger.warn("hEntries failed key={}: {}", key, e.toString());
            return Map.of();
        }
    }

    public void hPut(String key, String field, String value) {
        if (key == null || key.isBlank() || field == null || field.isBlank() || value == null) return;
        try {
            redisTemplate.opsForHash().put(key, field, value);
            applyExpire(key);
        } catch (Exception e) {
            logger.warn("hPut failed key={} field={}: {}", key, field, e.toString());
        }
    }

    public void hPutJson(String key, String field, Object value) {
        if (key == null || key.isBlank() || field == null || field.isBlank() || value == null) return;
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForHash().put(key, field, json);
            applyExpire(key);
        } catch (Exception e) {
            logger.warn("hPutJson failed key={} field={}: {}", key, field, e.toString());
        }
    }

    public <T> T hGetJson(String key, String field, TypeReference<T> typeRef) {
        if (key == null || key.isBlank() || field == null || field.isBlank()) return null;
        try {
            Object raw = redisTemplate.opsForHash().get(key, field);
            return objectMapper.readValue(String.valueOf(raw), typeRef);
        } catch (Exception e) {
            logger.warn("hGetJson failed key={} field={}: {}", key, field, e.toString());
            return null;
        }
    }

    public void expire(String key, int seconds) {
        if (key == null || key.isBlank() || seconds <= 0) return;
        try {
            redisTemplate.expire(key, Duration.ofSeconds(seconds));
        } catch (Exception e) {
            logger.warn("expire failed key={}: {}", key, e.toString());
        }
    }

    public void delete(String key) {
        if (key == null || key.isBlank()) return;
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            logger.warn("delete failed key={}: {}", key, e.toString());
        }
    }

    private void applyExpire(String key) {
        if (ttlSeconds > 0) {
            try {
                redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
            } catch (Exception e) {
                logger.debug("applyExpire failed key={}: {}", key, e.toString());
            }
        }
    }
}

