package com.spring.ai.app.rag.flow;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CarLoanStateMachine implements FlowStateMachine {

    @Override
    public String supportedIntent() {
        return "CAR_LOAN";
    }

    @Override
    public ChatResponse start(String userId, IntentResult ir) {
        // 首次进入：给用户一张“确认办理车贷”卡片
        return new ChatResponse(
                "card_list",
                List.of(new Card("confirm_car", "确认办理车贷", "", "intent", Map.of("i", "CAR_LOAN"))),
                "CAR_CONFIRM",
                UUID.randomUUID().toString()
        );
    }

    @Override
    public ChatResponse next(String userId, String text, Map<String, String> payload) {
        // 这里先简单返回文本，后续可扩展金额、期限等节点
        return new ChatResponse("text", "您已确认车贷，接下来请输入贷款金额（万元）", "CAR_AMOUNT", "");
    }
}