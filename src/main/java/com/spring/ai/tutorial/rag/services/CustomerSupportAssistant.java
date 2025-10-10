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
// import com.spring.ai.tutorial.rag.tools.TimeTools;
// import com.spring.ai.tutorial.rag.tools.WeatherTools;
import com.spring.ai.tutorial.rag.model.UserContext;
import com.spring.ai.tutorial.rag.config.MemoryConfig;
import com.spring.ai.tutorial.rag.security.PromptInjectionFilter;
import com.spring.ai.tutorial.rag.security.ResponseSecurityMonitor;
import com.spring.ai.tutorial.rag.security.SecurityAuditLogger;
import com.spring.ai.tutorial.rag.tools.TimeTools;
import com.spring.ai.tutorial.rag.tools.WeatherTools;
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
    private final PromptInjectionFilter promptInjectionFilter;
    private final ResponseSecurityMonitor responseSecurityMonitor;
    private final SecurityAuditLogger auditLogger;

    public CustomerSupportAssistant(Resource systemPromptResource,
                                    ChatClient.Builder modelBuilder,
                                    RetrievalAugmentationAdvisor retrievalAugmentationAdvisor,
                                    PromptChatMemoryAdvisor promptChatMemoryAdvisor,
                                    ObjectProvider<ToolCallbackProvider> toolCallbackProvider,
                                    ChatMemory chatMemory,
                                    MemoryConfig memoryConfig,
                                    PromptInjectionFilter promptInjectionFilter,
                                    ResponseSecurityMonitor responseSecurityMonitor,
                                    SecurityAuditLogger auditLogger) throws IOException {
        // @formatter:off
        var builder = modelBuilder
                .defaultSystem(systemPromptResource)
                .defaultTools( new ChangePlanTools(),new WeatherTools(),new TimeTools())
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
        this.promptInjectionFilter = promptInjectionFilter;
        this.responseSecurityMonitor = responseSecurityMonitor;
        this.auditLogger = auditLogger;
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
        // 获取用户ID（从userContext或chatId）
        String userId = userContext != null ? userContext.getUserName() : chatId;

        try {
            // 2. 输入安全检查
            PromptInjectionFilter.DetectionResult injectionResult =
                    promptInjectionFilter.detectInjection(userMessageContent);

            if (injectionResult.isMalicious()) {
                logger.warn("检测到恶意输入 - chatId: {}, userId: {}, riskScore: {}, reason: {}",
                        chatId, userId, injectionResult.getRiskScore(), injectionResult.getReason());

                // 记录攻击行为
                auditLogger.logInputCheck(chatId, userId, false, injectionResult.getRiskScore(), injectionResult.getReason());

                return "检测到异常请求格式，请使用正常的贷款咨询语言重新提问。";
            }

            // 3. 清理用户输入
            String sanitizedInput = promptInjectionFilter.sanitizeInput(userMessageContent);

            // 4. 记录正常请求
            auditLogger.logInputCheck(chatId, userId, true, 0.0, "安全检查通过");

            // 5. 获取对话历史记录
            List<Message> history = chatMemory.get(chatId);

            logger.debug("Original query: {}", userMessageContent);
            logger.debug("Sanitized query: {}", sanitizedInput);
            logger.debug("History messages count: {}", history.size());

            // 6. 将历史记录和当前查询拼接成特殊格式，以便QueryTransformer可以获取历史
            String enhancedQuery = formatQueryWithHistory(history, sanitizedInput);
            logger.debug("Enhanced query with history: {}", enhancedQuery);

            // 7. 构建system prompt参数
            var systemParams = buildSystemParams(userContext);

            // 打印最终发送到模型的参数
            logger.info("🤖 最终发送到AI模型的参数:");
            logger.info("📋 System参数: {}", systemParams);
            logger.info("📅 当前日期: {}", LocalDate.now().toString());
            logger.info("⏰ 当前时间: {}", java.time.LocalDateTime.now().toString());
            logger.info("👤 用户查询: {}", enhancedQuery);
            logger.info("🔧 Advisor参数: chatId={}, topK={}", chatId, memoryConfig.getTopK());

            // 8. 调用chatClient获取响应
            String response = this.chatClient.prompt()
                    .system(s -> {
                        systemParams.forEach(s::param);
                        s.param("current_date", LocalDate.now().toString());
                        s.param("current_time", java.time.LocalDateTime.now().toString());
                    })
                    .user(enhancedQuery)
                    .advisors(a -> a.param(CONVERSATION_ID, chatId).param(TOP_K, memoryConfig.getTopK()))
                    .call()
                    .content();

            // 9. 响应安全检查
            ResponseSecurityMonitor.SecurityCheckResult securityResult =
                    responseSecurityMonitor.checkResponse(userMessageContent, response);

            if (!securityResult.isSafe()) {
                logger.error("响应安全检查失败 - chatId: {}, userId: {}, riskScore: {}, reason: {}",
                        chatId, userId, securityResult.getRiskScore(), securityResult.getReason());

                auditLogger.logOutputCheck(chatId, userId, false, securityResult.getRiskScore(), securityResult.getReason());

                return "抱歉，系统检测到异常响应，请稍后再试或联系客服。";
            }

            auditLogger.logOutputCheck(chatId, userId, true, 0.0, "响应安全检查通过");

            return response;

        } catch (Exception e) {
            logger.error("处理聊天请求时发生异常 - chatId: {}, userId: {}", chatId, userId, e);
            auditLogger.logSystemException(chatId, userId, e.getClass().getSimpleName(), e.getMessage());
            return "系统处理异常，请稍后再试。";
        }
    }

    private Map<String, Object> buildSystemParams(UserContext userContext) {
        Map<String, Object> params = new HashMap<>();

        // 添加用户上下文信息
        if (userContext != null) {
            // 若 UserContext 未来扩展了对应方法再打开
            // params.put("userName", userContext.getUserName());
            // params.put("userPlan", userContext.getUserPlan());
            // params.put("userRegion", userContext.getUserRegion());
        }

        // 添加时间工具
        // params.put("currentTime", TimeTools.getCurrentTime());
        // params.put("currentDate", TimeTools.getCurrentDate());

        // 添加天气工具
        // params.put("currentWeather", WeatherTools.getCurrentWeather());

        return params;
    }
}
