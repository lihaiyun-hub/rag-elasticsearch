package com.spring.ai.tutorial.rag.flow;

import java.util.Map;

public interface FlowStateMachine {
    /**
     * 该状态机负责的意图常量
     */
    String supportedIntent();

    /**
     * 首次进入流程
     */
    ChatResponse start(String userId, IntentResult ir);

    /**
     * 后续节点推进
     */
    ChatResponse next(String userId, String text, Map<String, String> payload);
}