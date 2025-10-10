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

package com.spring.ai.tutorial.rag.services;

import com.spring.ai.tutorial.rag.tools.ChangePlanTools;
import com.spring.ai.tutorial.rag.tools.TimeTools;
import com.spring.ai.tutorial.rag.tools.WeatherTools;
import com.spring.ai.tutorial.rag.model.UserContext;
import com.spring.ai.tutorial.rag.config.MemoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor.TOP_K;
import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;


@Service
public class CustomerSupportAssistant {

    private static final Logger logger = LoggerFactory.getLogger(CustomerSupportAssistant.class);
    private ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final MemoryConfig memoryConfig;

    public CustomerSupportAssistant(Resource systemPromptResource,
                                    ChatClient.Builder modelBuilder,
                                    RetrievalAugmentationAdvisor retrievalAugmentationAdvisor,
                                    PromptChatMemoryAdvisor promptChatMemoryAdvisor,
                                    ObjectProvider<ToolCallbackProvider> toolCallbackProvider,
                                    ChatMemory chatMemory,
                                    MemoryConfig memoryConfig) throws IOException {
        // @formatter:off
        var builder = modelBuilder
                .defaultSystem(systemPromptResource)
                .defaultTools( new ChangePlanTools())
                .defaultAdvisors(retrievalAugmentationAdvisor, promptChatMemoryAdvisor)
                // 当 ToolCallbackProvider 不存在时，跳过工具回调注册，保证应用可启动
                ;
        var provider = toolCallbackProvider != null ? toolCallbackProvider.getIfAvailable() : null;
        if (provider != null) {
            builder = builder.defaultToolCallbacks(provider.getToolCallbacks());
        }
        builder = builder.defaultOptions(ToolCallingChatOptions.builder()
                        .internalToolExecutionEnabled(true)
                        .build());

        this.chatClient = builder.build();
        this.chatMemory = chatMemory;
        this.memoryConfig = memoryConfig;
        // @formatter:on
    }

    /**
     * 将历史记录和当前查询格式化成特殊格式，以便QueryTransformer可以解析
     */
    private String formatQueryWithHistory(List<Message> history, String currentQuery) {
        StringBuilder formatted = new StringBuilder();
        
        // 添加历史记录分隔符
        formatted.append("===HISTORY_START===\n");
        for (Message message : history) {
            formatted.append(message.getMessageType()).append(":")
                    .append(message.getText()).append("\n");
        }
        formatted.append("===HISTORY_END===\n");
        
        // 添加当前查询
        formatted.append("===CURRENT_QUERY===\n");
        formatted.append(currentQuery);
        
        return formatted.toString();
    }

    public String chat(String chatId, String userMessageContent) {
        return chat(chatId, userMessageContent, null);
    }

    public String chat(String chatId, String userMessageContent, UserContext userContext) {
        try {
            // 获取对话历史记录
            List<Message> history = chatMemory.get(chatId);
            
            logger.debug("Original query: {}", userMessageContent);
            logger.debug("History messages count: {}", history.size());

            // 将历史记录和当前查询拼接成特殊格式，以便QueryTransformer可以获取历史
            String enhancedQuery = formatQueryWithHistory(history, userMessageContent);
            logger.debug("Enhanced query with history: {}", enhancedQuery);

            // 构建system prompt参数
            var systemParams = buildSystemParams(userContext);

            return this.chatClient.prompt()
                    .system(s -> {
                        systemParams.forEach(s::param);
                        s.param("current_date", LocalDate.now().toString());
                        s.param("current_time", java.time.LocalDateTime.now().toString());
                    })
                    .user(enhancedQuery)
                    .advisors(a -> a.param(CONVERSATION_ID, chatId).param(TOP_K, memoryConfig.getTopK()))
                    .call()
                    .content();
        } catch (Exception e) {
            logger.error("Assistant chat processing failed", e);
            return "抱歉，当前服务繁忙或工具调用出现问题，请稍后重试。";
        }
    }

    /**
     * 构建system prompt所需的参数
     */
    private Map<String, String> buildSystemParams(UserContext userContext) {
        Map<String, String> params = new HashMap<>();
        
        if (userContext != null) {
            params.put("user_name", userContext.getUserName());
            params.put("available_credit", String.valueOf(userContext.getAvailableCredit()));
            params.put("current_loan_plan", userContext.getCurrentLoanPlan());
            params.put("recent_repayment_status", userContext.getRecentRepaymentStatus());
            params.put("max_loan_amount", String.valueOf(userContext.getMaxLoanAmount()));
        } else {
            // 默认值
            params.put("user_name", "尊敬的客户");
            params.put("available_credit", "10000");
            params.put("current_loan_plan", "无");
            params.put("recent_repayment_status", "正常");
            params.put("max_loan_amount", "50000");
        }
        
        return params;
    }
}
