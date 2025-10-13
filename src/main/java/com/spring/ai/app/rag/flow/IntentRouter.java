package com.spring.ai.app.rag.flow;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class IntentRouter {
    private final List<FlowStateMachine> machines;

    public IntentRouter(List<FlowStateMachine> machines) {
        this.machines = machines;
    }

    public ChatResponse route(String userId, IntentResult ir) {
        // 找到匹配的状态机
        FlowStateMachine machine = machines.stream()
                .filter(m -> m.supportedIntent().equals(ir.intent()))
                .findFirst()
                .orElse(null);
        if (machine == null) {
            return null; // 没有匹配的状态机则回落旧逻辑
        }

        // 若携带了 current_step，则进入推进(next)而不是重新开始(start)
        Map<String, String> slots = ir.slots();
        if (slots != null && slots.containsKey("current_step")) {
            return machine.next(userId, "", slots);
        }

        // 默认执行首次进入
        return machine.start(userId, ir);
    }
}