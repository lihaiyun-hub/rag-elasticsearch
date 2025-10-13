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
        你是贷款意图识别专家，专门分析用户的贷款需求意图。
        你的任务是准确识别用户想要咨询的贷款类型，并提取关键信息。
        
        要求：
        1. 只输出 JSON 格式，不要有任何解释或额外文字
        2. 严格按照指定的意图分类进行判断
        3. 准确提取金额、期限等关键槽位信息
        4. 置信度要准确反映判断的确定性
        
        输出必须包含：intent, confidence, slots 三个字段
        """;

    public IntentResult extract(String userMessage) {
        BeanOutputConverter<IntentResult> converter = new BeanOutputConverter<>(IntentResult.class);
        String userPrompt = "请将以下用户输入分类为贷款意图，并提取关键信息：\n\n"
                      + "意图分类选项：\n"
                      + "- CONSUMER_LOAN: 消费贷、信用贷、小额借款、急用钱、日常消费、授信、额度评估、开通额度、申请额度\n"
                      + "- UNKNOWN: 以上都不是\n\n"
                      + "输出格式要求：\n"
                      + "{\"intent\":\"意图分类\",\"confidence\":0.0~1.0,\"slots\":{\"amount\":\"金额\",\"term\":\"期限\"}}\n\n"
                      + "用户输入：" + userMessage;
        String raw = chatClientBuilder.build()
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();
        return converter.convert(raw);
    }
}