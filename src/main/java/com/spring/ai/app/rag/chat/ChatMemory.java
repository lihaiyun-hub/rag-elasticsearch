package com.spring.ai.app.rag.chat;

import com.spring.ai.app.rag.model.Message;
import java.util.List;

/**
 * 聊天记忆接口
 */
public interface ChatMemory {
    /**
     * 会话ID参数名
     */
    String CONVERSATION_ID = "conversation_id";

    /**
     * 获取指定会话ID的聊天历史记录
     */
    List<Message> get(String chatId);

    /**
     * 添加消息到指定会话的历史记录
     */
    void add(String chatId, Message message);

    /**
     * 清除指定会话的历史记录
     */
    void clear(String chatId);
}