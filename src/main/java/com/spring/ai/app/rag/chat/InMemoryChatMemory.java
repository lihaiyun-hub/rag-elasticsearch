package com.spring.ai.app.rag.chat;

import com.spring.ai.app.rag.model.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存版聊天历史存储（按 conversation_id 隔离）
 */
@Component
public class InMemoryChatMemory implements ChatMemory {

    /* 每个 conversation 对应一个线程安全的消息列表 */
    private final Map<String, List<Message>> store = new ConcurrentHashMap<>();

    /* 保留的最大消息条数（以消息计数；一轮通常包含用户+助手两条） */
    private volatile int maxMessages;

    public InMemoryChatMemory(
            @Value("${spring.ai.chat.memory.max-messages:20}") int defaultMaxMessages,
            @Value("${spring.ai.chat.memory.max-rounds:0}") int defaultMaxRounds
    ) {
        // 若设置了按“轮数”，优先使用轮数（每轮约两条消息）
        if (defaultMaxRounds > 0) {
            this.maxMessages = defaultMaxRounds * 2;
        } else {
            this.maxMessages = Math.max(defaultMaxMessages, 0);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        return Optional.ofNullable(store.get(conversationId))
                      .map(Collections::unmodifiableList) // 只读透出
                      .orElse(Collections.emptyList());
    }

    @Override
    public void add(String conversationId, Message message) {
        /* computeIfAbsent 保证并发下只创建一个 List */
        List<Message> list = store.computeIfAbsent(conversationId, k -> new CopyOnWriteArrayList<>());
        list.add(message);
        trimToLimit(list);
    }

    @Override
    public void clear(String conversationId) {
        store.remove(conversationId);
    }

    /* 运行时更新：设置保留的历史“轮数”（每轮约两条消息） */
    public void setMaxRounds(int rounds) {
        if (rounds <= 0) {
            this.maxMessages = 0; // 不保留历史
        } else {
            this.maxMessages = rounds * 2;
        }
    }

    /* 运行时更新：直接设置保留的最大消息条数 */
    public void setMaxMessages(int maxMessages) {
        this.maxMessages = Math.max(maxMessages, 0);
    }

    public int getMaxRounds() {
        return (maxMessages <= 0) ? 0 : (maxMessages / 2);
    }

    public int getMaxMessages() {
        return Math.max(maxMessages, 0);
    }

    private void trimToLimit(List<Message> list) {
        int limit = Math.max(maxMessages, 0);
        if (limit == 0) {
            list.clear();
            return;
        }
        while (list.size() > limit) {
            // 移除最早的消息，保留最新的 limit 条
            list.remove(0);
        }
    }
}
