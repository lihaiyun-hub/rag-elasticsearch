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

import com.spring.ai.tutorial.rag.tools.TimeTools;
import com.spring.ai.tutorial.rag.tools.WeatherTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import java.time.LocalDate;

import static org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor.TOP_K;
import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * * @author Christian Tzolov
 * 模拟的是一个航空公司 Funnair 的客户支持助手，具备：
 * 自然语言交互（ChatClient）
 * 记忆能力（ChatMemory）
 * 知识检索（RAG via VectorStore）
 * 函数调用（Function Calling）
 */
@Service
public class CustomerSupportAssistant {


    private ChatClient chatClient;

    public CustomerSupportAssistant(Resource systemPromptResource, ChatClient.Builder modelBuilder, RetrievalAugmentationAdvisor retrievalAugmentationAdvisor, PromptChatMemoryAdvisor promptChatMemoryAdvisor) throws IOException {


        // @formatter:off
        this.chatClient = modelBuilder
                .defaultSystem(systemPromptResource)
                .defaultTools(new TimeTools(), new WeatherTools())
                .defaultAdvisors(promptChatMemoryAdvisor, retrievalAugmentationAdvisor)
                .defaultOptions(ToolCallingChatOptions.builder()
                        .internalToolExecutionEnabled(true)
                        .build())
                .build();
        // @formatter:on
    }

    public String chat(String chatId, String userMessageContent) {

        return this.chatClient.prompt()
                .system(s -> s.param("current_date", LocalDate.now().toString()))
                .user(userMessageContent)
                .advisors(
                        // 设置advisor参数，
                        // 记忆使用chatId，
                        // 拉取最近的100条记录
                        a -> a.param(CONVERSATION_ID, chatId).param(TOP_K, 100))
                .call()
                .content();
    }




}
