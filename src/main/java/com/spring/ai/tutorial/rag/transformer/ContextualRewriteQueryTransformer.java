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

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.core.io.Resource;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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