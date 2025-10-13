package com.spring.ai.app.rag.flow;

import java.util.Map;

public record IntentResult(
        String intent,        // HOUSE_LOAN / CREDIT_CARD / CONSUMER_LOAN / CONSUMER_CREDIT / UNKNOWN
        double confidence,
        Map<String, String> slots,
        Boolean consumerLoanAuthorized   // null 表示未查询或无需授信；true/false 表示已授信状态
) {}