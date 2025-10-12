package com.spring.ai.app.rag.flow;

public record ChatResponse(
        String type,    // text | card_list | tool_result | form
        Object payload, // 对应内容
        String state,   // 当前状态机节点
        String trackingId
) {}