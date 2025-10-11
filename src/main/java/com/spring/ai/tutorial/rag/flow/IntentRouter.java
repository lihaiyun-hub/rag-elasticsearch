package com.spring.ai.tutorial.rag.flow;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IntentRouter {
    private final List<FlowStateMachine> machines;

    public IntentRouter(List<FlowStateMachine> machines) {
        this.machines = machines;
    }

    public ChatResponse route(String userId, IntentResult ir) {
        return machines.stream()
                .filter(m -> m.supportedIntent().equals(ir.intent()))
                .findFirst()
                .map(m -> m.start(userId, ir))
                .orElse(null);   // 兜底返回 null，外部会回落旧逻辑
    }
}