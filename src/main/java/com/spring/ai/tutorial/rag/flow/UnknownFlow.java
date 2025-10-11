package com.spring.ai.tutorial.rag.flow;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class UnknownFlow implements FlowStateMachine {
    @Override
    public String supportedIntent() {
        return "UNKNOWN";
    }

    @Override
    public ChatResponse start(String userId, IntentResult ir) {
        // 返回 null 表示回落旧 RAG 流程
        return null;
    }

    @Override
    public ChatResponse next(String userId, String text, Map<String, String> payload) {
        return null;
    }
}