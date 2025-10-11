package com.spring.ai.tutorial.rag.flow;

import java.util.Map;

public record Card(
        String id,
        String title,
        String subtitle,
        String actionType,        // intent | slot | navigate | submit
        Map<String,String> payload   // 携带参数
) {}