package com.spring.ai.app.rag.flow;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IntentExtractor {

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    private static final String SYSTEM_PROMPT = """
        你是贷款意图识别器，只输出 JSON，不解释。
        规则：
        1. 买车、车辆分期、车牌贷、车辆抵押 → CAR_LOAN
        2. 购房、按揭、房抵 → HOUSE_LOAN
        3. 信用卡、分期卡 → CREDIT_CARD
        4. 其余 → UNKNOWN
        输出格式：
        {"intent":"CAR_LOAN|HOUSE_LOAN|CREDIT_CARD|UNKNOWN","confidence":0.0~1.0,"slots":{}}
        """;

    public IntentResult extract(String userMessage) {
        BeanOutputConverter<IntentResult> converter = new BeanOutputConverter<>(IntentResult.class);
        String prompt = "将用户输入分类为以下意图：CAR_LOAN, HOUSE_LOAN, CREDIT_CARD, UNKNOWN。\n"
                      + "同时提取关键槽位（如金额、期限）。\n"
                      + "以JSON格式返回，字段：intent(string), confidence(double), slots(map)。\n"
                      + "用户输入：" + userMessage;
        String raw = chatClientBuilder.build().prompt().user(prompt).call().content();
        return converter.convert(raw);
    }
}