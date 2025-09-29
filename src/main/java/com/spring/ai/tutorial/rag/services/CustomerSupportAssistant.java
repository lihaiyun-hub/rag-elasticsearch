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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.time.LocalDate;

import static org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor.TOP_K;
import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;


@Service
public class CustomerSupportAssistant {

    private static final Logger logger = LoggerFactory.getLogger(CustomerSupportAssistant.class);
    private ChatClient chatClient;

    public CustomerSupportAssistant(Resource systemPromptResource,
                                    ChatClient.Builder modelBuilder,
                                    RetrievalAugmentationAdvisor retrievalAugmentationAdvisor,
                                    PromptChatMemoryAdvisor promptChatMemoryAdvisor,
                                    ToolCallbackProvider tools) throws IOException {
        // @formatter:off
        var builder = modelBuilder
                .defaultSystem(systemPromptResource)
                .defaultTools( new ChangePlanTools())
                .defaultAdvisors(promptChatMemoryAdvisor)
                .defaultToolCallbacks(tools.getToolCallbacks())
                .defaultOptions(ToolCallingChatOptions.builder()
                        .internalToolExecutionEnabled(true)
                        .build());

        this.chatClient = builder.build();
        // @formatter:on
    }

    public String chat(String chatId, String userMessageContent) {
        try {

            return this.chatClient.prompt()
                    .system(s -> s.param("current_date", LocalDate.now().toString()))
                    .user(userMessageContent)
                    .advisors(a -> a.param(CONVERSATION_ID, chatId).param(TOP_K, 100))
                    .call()
                    .content();
        } catch (Exception e) {
            logger.error("Assistant chat processing failed", e);
            return "抱歉，当前服务繁忙或工具调用出现问题，请稍后重试。";
        }
    }
}
