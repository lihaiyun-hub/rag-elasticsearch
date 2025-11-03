package com.spring.ai.app.rag.chat;

import org.springframework.core.io.Resource;

import java.util.function.Consumer;

/**
 * 聊天客户端接口
 */
public interface ChatClient {
    /**
     * 创建一个新的提示构建器
     */
    PromptBuilder prompt();

    /**
     * 聊天客户端构建器
     */
    interface Builder {
        /**
         * 设置默认系统提示词
         */
        Builder defaultSystem(Resource systemPrompt);

        /**
         * 设置默认顾问
         */
        Builder defaultAdvisors(PromptChatMemoryAdvisor... advisors);

        /**
         * 设置默认选项
         */
        Builder defaultOptions(ToolCallingChatOptions options);

        /**
         * 构建聊天客户端
         */
        ChatClient build();
    }

    /**
     * 提示构建器接口
     */
    interface PromptBuilder {
        /**
         * 设置系统提示词
         */
        PromptBuilder system(Consumer<SystemParamSetter> paramSetter);

        /**
         * 设置用户提示词
         */
        PromptBuilder user(String prompt);

        /**
         * 设置顾问参数
         */
        PromptBuilder advisors(AdvisorParamSetter paramSetter);

        /**
         * 执行调用
         */
        ChatResponse call();
    }

    interface SystemParamSetter {
        SystemParamSetter param(String key, Object value);
    }

    /**
     * 顾问参数设置器接口
     */
    interface AdvisorParamSetter {
        /**
         * 设置顾问参数
         */
        void param(String key, Object value);
    }

    /**
     * 聊天响应接口
     */
    interface ChatResponse {
        /**
         * 获取响应内容
         */
        String content();
    }
}