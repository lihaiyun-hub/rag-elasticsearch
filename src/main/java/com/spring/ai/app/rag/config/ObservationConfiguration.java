package com.spring.ai.app.rag.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.spring.ai.app.rag.observation.ChatModelObservationContext;
import com.spring.ai.app.rag.observation.EmbeddingModelObservationContext;
import com.spring.ai.app.rag.observation.ToolCallingObservationContext;

@Configuration
public class ObservationConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ObservationConfiguration.class);

    /**
     * 可选：如果需要自定义ObservationRegistry配置，可以保留此方法
     * 但不再手动注册ObservationHandler，让Spring Boot自动配置处理
     */
    @Bean
    @ConditionalOnMissingBean(name = "observationRegistry")
    public ObservationRegistry observationRegistry() {
        // 让Spring Boot自动配置处理ObservationHandler的注册
        return ObservationRegistry.create();
    }

    /**
     * 监听chat model调用
     */
    @Bean
    ObservationHandler<ChatModelObservationContext> chatModelObservationContextObservationHandler() {
        logger.info("ChatModelObservation start");
        return new ObservationHandler<>() {

            @Override
            public boolean supportsContext(Observation.Context context) {
                return context instanceof ChatModelObservationContext;
            }

            @Override
            public void onStart(ChatModelObservationContext context) {
                logger.info(" ChatModel请求开始 - 操作类型: {}, 提供者: {}, 请求内容: {}",
                    context.getOperationType(),
                    context.getProvider(),
                    context.getRequest());
            }

            @Override
            public void onStop(ChatModelObservationContext context) {
                logger.info(" ChatModel请求结束 - 响应内容: {}", context.getResponse());
            }
        };
    }

    /**
     * 监听工具调用
     */
    @Bean
    public ObservationHandler<ToolCallingObservationContext> toolCallingObservationContextObservationHandler() {
        logger.info("ToolCallingObservation start");
        return new ObservationHandler<>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return context instanceof ToolCallingObservationContext;
            }

            @Override
            public void onStart(ToolCallingObservationContext context) {
                logger.info("ToolCalling start: {} - {}", context.getToolName(), context.getToolArguments());
            }

            @Override
            public void onStop(ToolCallingObservationContext context) {
                logger.info("ToolCalling done: {} - {}", context.getToolName(), context.getToolResult());
            }
        };
    }

    /**
     * 监听embedding model调用
     */
    @Bean
    public ObservationHandler<EmbeddingModelObservationContext> embeddingModelObservationContextObservationHandler() {
        logger.info("EmbeddingModelObservation start");
        return new ObservationHandler<>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return context instanceof EmbeddingModelObservationContext;
            }

            @Override
            public void onStart(EmbeddingModelObservationContext context) {
                logger.info("EmbeddingModelObservation start: {} - {}", 
                    context.getOperationType(),
                    context.getProvider());
            }
        };
    }
}