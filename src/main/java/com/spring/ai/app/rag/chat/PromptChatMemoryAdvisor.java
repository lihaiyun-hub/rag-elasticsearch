package com.spring.ai.app.rag.chat;

import com.spring.ai.app.rag.model.Message;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 提示词聊天记忆顾问
 */
@Component
public class PromptChatMemoryAdvisor {
    private final ChatMemory chatMemory;

    public PromptChatMemoryAdvisor(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    /**
     * 获取指定会话的历史记录
     */
    public List<Message> getHistory(String chatId) {
        return chatMemory.get(chatId);
    }

    /**
     * 添加消息到指定会话的历史记录
     */
    public void addMessage(String chatId, Message message) {
        chatMemory.add(chatId, message);
    }

    /**
     * 清除指定会话的历史记录
     */
    public void clearHistory(String chatId) {
        chatMemory.clear(chatId);
    }

    /**
     * 设置参数
     */
    public void setParam(Map<String, Object> params, String key, Object value) {
        params.put(key, value);
    }
}