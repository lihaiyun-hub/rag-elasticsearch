package com.spring.ai.tutorial.rag.flow;

import java.util.Map;

public record IntentResult(
        String intent,        // CAR_LOAN / HOUSE_LOAN / CREDIT_CARD / UNKNOWN
        double confidence,
        Map<String, String> slots
) {}