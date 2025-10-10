/*
 * Copyright 2024-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.spring.ai.tutorial.rag.transformer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

public class ContextualRewriteQueryTransformer implements QueryTransformer {

    private static final Logger logger = LoggerFactory.getLogger(ContextualRewriteQueryTransformer.class);
    private final ChatClient chatClient;
    private final PromptTemplate promptTemplate;

    public ContextualRewriteQueryTransformer(ChatClient.Builder chatClientBuilder, Resource customPromptResource) {
        this.chatClient = chatClientBuilder.build();
        
        // 加载提示模板
        Resource promptResource = customPromptResource != null ? customPromptResource :
                new DefaultResourceLoader().getResource("classpath:prompts/contextual-rewrite-prompt.st");
        
        this.promptTemplate = PromptTemplate.builder()
                .resource(promptResource)
                .build();
    }

    @Override
    public Query transform(Query query) {
        // 获取用户消息
        String userText = query.text();
        
        // 处理简单的问候语查询
        String processedQuery = handleSimpleQueries(userText);
        if (!processedQuery.equals(userText)) {
            logger.debug("Simple query detected and rewritten: {} -> {}", userText, processedQuery);
            return Query.builder()
                    .text(processedQuery)
                    .build();
        }
        
        // 解析可能包含历史记录的增强查询格式
        QueryHistoryPair parsed = parseEnhancedQuery(userText);
        String history = parsed.history;
        String currentQuery = parsed.currentQuery;
        
        logger.debug("Parsed history: {}", history.isEmpty() ? "empty" : "present");
        logger.debug("Current query: {}", currentQuery);

        // 构建提示（现在包含历史记录）
        String prompt = promptTemplate.render(java.util.Map.of(
                "history", history,
                "query", currentQuery
        ));

        // 调用模型重写查询
        ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
        String rewrittenQuery = response.getResult().getOutput().getText();

        // 返回重写后的查询
        return Query.builder()
                .text(rewrittenQuery)
                .build();
    }

    /**
     * 解析增强查询格式，分离历史记录和当前查询
     */
    private QueryHistoryPair parseEnhancedQuery(String enhancedQuery) {
        String history = "";
        String currentQuery = enhancedQuery;
        
        // 检查是否包含历史记录标记
        int historyStart = enhancedQuery.indexOf("===HISTORY_START===");
        int historyEnd = enhancedQuery.indexOf("===HISTORY_END===");
        int queryStart = enhancedQuery.indexOf("===CURRENT_QUERY===");
        
        if (historyStart != -1 && historyEnd != -1 && queryStart != -1) {
            // 提取历史记录
            history = enhancedQuery.substring(historyStart + "===HISTORY_START===".length(), historyEnd).trim();
            
            // 提取当前查询
            currentQuery = enhancedQuery.substring(queryStart + "===CURRENT_QUERY===".length()).trim();
            
            logger.debug("Successfully parsed enhanced query format");
        } else {
            logger.debug("No enhanced query format detected, using original query");
        }
        
        return new QueryHistoryPair(history, currentQuery);
    }
    
    /**
     * 处理简单的问候语和常见查询，避免被错误重写
     */
    private String handleSimpleQueries(String query) {
        String cleanQuery = query.trim().toLowerCase();
        
        // 问候语处理
        if (cleanQuery.matches("^(你好|您好|hi|hello|在吗|有人吗)$")) {
            return "客服问候语和开场白";
        }
        
        // 简单数字处理（避免被误解为金额）
        if (cleanQuery.matches("^\\d+$")) {
            return "借款" + query + "元";
        }
        
        // 模糊表达处理
        if (cleanQuery.contains("借不了") || cleanQuery.contains("借不到")) {
            return "借款失败原因";
        }
        
        if (cleanQuery.contains("提额") || cleanQuery.contains("提额度")) {
            return "提升额度";
        }
        
        if (cleanQuery.contains("提前还")) {
            return "提前还款";
        }
        
        if (cleanQuery.contains("额度") && cleanQuery.length() < 5) {
            return "额度查询";
        }
        
        // 保持原查询
        return query;
    }
    
    /**
     * 内部类，用于存储解析后的历史记录和查询
     */
    private static class QueryHistoryPair {
        final String history;
        final String currentQuery;
        
        QueryHistoryPair(String history, String currentQuery) {
            this.history = history;
            this.currentQuery = currentQuery;
        }
    }
}