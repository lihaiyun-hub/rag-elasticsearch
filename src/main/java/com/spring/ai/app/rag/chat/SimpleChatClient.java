package com.spring.ai.app.rag.chat;


import com.spring.ai.app.rag.model.KnowledgeRecord;
import com.spring.ai.app.rag.model.Message;
import com.spring.ai.app.rag.services.LargeLanguageModelService;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 轻量版 ChatClient 实现，提供 PromptBuilder 链式调用
 * 对齐 Spring AI 的 ChatClient 使用体验，但不引入检索逻辑。
 */
public class SimpleChatClient implements ChatClient {

    private final LargeLanguageModelService chatModel;
    private final String defaultSystemPrompt;
    private final PromptChatMemoryAdvisor defaultAdvisor;
    private final ToolCallingChatOptions defaultOptions;



    private SimpleChatClient(Builder builder) {
        this.chatModel = builder.chatModel;
        this.defaultSystemPrompt = builder.defaultSystemPrompt;
        this.defaultAdvisor = builder.defaultAdvisors.isEmpty() ? null : builder.defaultAdvisors.get(0);
        this.defaultOptions = builder.defaultOptions != null ? builder.defaultOptions : ToolCallingChatOptions.builder().build();

    }

    @Override
    public PromptBuilder prompt() {
        return new PromptBuilderImpl(chatModel, defaultSystemPrompt, defaultAdvisor, defaultOptions);
    }

    /**
     * 构建器
     */
    public static class Builder implements ChatClient.Builder {
        private final LargeLanguageModelService chatModel;
        private String defaultSystemPrompt;
        private final List<PromptChatMemoryAdvisor> defaultAdvisors = new ArrayList<>();
        private ToolCallingChatOptions defaultOptions;



        public Builder(LargeLanguageModelService chatModel) {
            this.chatModel = chatModel;
        }



        @Override
        public Builder defaultSystem(Resource systemPrompt) {
            if (systemPrompt != null) {
                try {
                    this.defaultSystemPrompt = new String(systemPrompt.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException("读取系统提示词失败", e);
                }
            }
            return this;
        }

        @Override
        public Builder defaultAdvisors(PromptChatMemoryAdvisor... advisors) {
            this.defaultAdvisors.addAll(List.of(advisors));
            return this;
        }

        @Override
        public Builder defaultOptions(ToolCallingChatOptions options) {
            this.defaultOptions = options;
            return this;
        }

        @Override
        public ChatClient build() {
            return new SimpleChatClient(this);
        }
    }

    /**
     * PromptBuilder 实现
     */
    private static class PromptBuilderImpl implements ChatClient.PromptBuilder {
        private final LargeLanguageModelService chatModel;
        private final PromptChatMemoryAdvisor advisor;
        private final ToolCallingChatOptions options;

        private String systemTemplate;
        private final Map<String, Object> systemParams = new HashMap<>();
        private String userPrompt;
        private final Map<String, Object> advisorParams = new HashMap<>();

        private PromptBuilderImpl(LargeLanguageModelService chatModel,
                                  String defaultSystem,
                                  PromptChatMemoryAdvisor advisor,
                                  ToolCallingChatOptions options) {
            this.chatModel = chatModel;
            this.systemTemplate = defaultSystem;
            this.advisor = advisor;
            this.options = options;
        }

        @Override
        public PromptBuilder system(Consumer<SystemParamSetter> paramSetter) {
            paramSetter.accept((key, value) -> {
                systemParams.put(key, value);
                return null;
            });
            return this;
        }



        @Override
        public PromptBuilder user(String prompt) {
            this.userPrompt = prompt;
            return this;
        }

        @Override
        public PromptBuilder advisors(ChatClient.AdvisorParamSetter paramSetter) {
            paramSetter.param("", null);
            return this;
        }

        @Override
        public ChatClient.ChatResponse call() {
            List<Message> messages = new ArrayList<>();

            // 1) 系统提示词
            if (systemTemplate != null && !systemTemplate.isEmpty()) {
                String applied = applyParams(systemTemplate, systemParams);
                messages.add(Message.builder().type(Message.Type.SYSTEM).content(applied).build());
            }

            // 2) 历史记忆（可选）
            if (advisor != null && advisorParams.containsKey(ChatMemory.CONVERSATION_ID)) {
                String chatId = String.valueOf(advisorParams.get(ChatMemory.CONVERSATION_ID));
                List<Message> history = advisor.getHistory(chatId);
                messages.addAll(history);
            }

            // 3) 用户消息
            if (userPrompt != null && !userPrompt.isEmpty()) {
                messages.add(Message.builder().type(Message.Type.USER).content(userPrompt).build());
            }

            // 4) 调用 ChatModel
            String content = chatModel.chatMessages(messages);
            return () -> content;
        }

        private String applyParams(String template, Map<String, Object> params) {
            String result = template;
            for (Map.Entry<String, Object> e : params.entrySet()) {
                String key = e.getKey();
                String val = e.getValue() == null ? "" : String.valueOf(e.getValue());
                // 支持三种占位风格：${key}、{{key}}、{key}
                result = result.replace("${" + key + "}", val);
                result = result.replace("{{" + key + "}}", val);
                result = result.replace("{" + key + "}", val);
            }
            return result;
        }
    }


}
