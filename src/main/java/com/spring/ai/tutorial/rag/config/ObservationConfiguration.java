package com.spring.ai.tutorial.rag.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.observation.ChatClientObservationContext;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationContext;
import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

/**
 * @author yingzi
 * @since 2025/6/12
 */
@Configuration
public class ObservationConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ObservationConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(name = "observationRegistry")
    public ObservationRegistry observationRegistry(
            ObjectProvider<ObservationHandler<?>> observationHandlerObjectProvider) {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        ObservationRegistry.ObservationConfig observationConfig = observationRegistry.observationConfig();
        observationHandlerObjectProvider.orderedStream().forEach(handler -> {
            Type[] genericInterfaces = handler.getClass().getGenericInterfaces();
            for (Type type : genericInterfaces) {
                if (type instanceof ParameterizedType parameterizedType
                        && parameterizedType.getRawType() instanceof Class<?> clazz
                        && ObservationHandler.class.isAssignableFrom(clazz)) {

                    Type actualTypeArgument = parameterizedType.getActualTypeArguments()[0];
                    logger.info("load observation handler, supports context type: {}", actualTypeArgument);
                }
            }

            // 将handler添加到observationRegistry中
            observationConfig.observationHandler(handler);
        });
        return observationRegistry;
    }

    /**
     * 监听chat client调用
     */
    @Bean
    ObservationHandler<ChatClientObservationContext> chatClientObservationContextObservationHandler() {
        logger.info("ChatClientObservation start");
        return new ObservationHandler<>() {

            @Override
            public boolean supportsContext(Observation.Context context) {
                return context instanceof ChatClientObservationContext;
            }

            @Override
            public void onStart(ChatClientObservationContext context) {
                List<? extends Advisor> advisors = context.getAdvisors();
                boolean stream = context.isStream();
                
                logger.info(" ChatClient请求 - 顾问数量: {}, 流式: {}",
                        advisors != null ? advisors.size() : 0, 
                        stream);
                
                // 只在有顾问时打印详细信息
                if (advisors != null && !advisors.isEmpty()) {
                    logger.info(" 激活的顾问: {}",
                            advisors.stream().map(a -> a.getName() + "(order:" + a.getOrder() + ")")
                                    .collect(java.util.stream.Collectors.joining(", ")));
                }
            }

            @Override
            public void onStop(ChatClientObservationContext context) {
                ObservationHandler.super.onStop(context);
            }
        };
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
                AiOperationMetadata operationMetadata = context.getOperationMetadata();
                Prompt request = context.getRequest();
                
                if (request != null && request.getInstructions() != null) {
                    logger.info("AI模型请求 - 操作: {}, 指令数量: {}",
                            operationMetadata != null ? operationMetadata.operationType() : "unknown",
                            request.getInstructions().size());
                    
                    // 只打印关键信息，避免重复
                    request.getInstructions().forEach(instruction -> {
                        logger.info("   {}: {}", instruction.getMessageType(),
                                instruction.getText().length() > 500 ?
                                instruction.getText().substring(0, 500) + "..." :
                                instruction.getText());
                    });
                }
            }

            @Override
            public void onStop(ChatModelObservationContext context) {
                ChatResponse response = context.getResponse();
                logger.info("ChatModelObservation start: ChatResponse : {}",
                        response);
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
                ToolDefinition toolDefinition = context.getToolDefinition();
                logger.info("ToolCalling start: {} - {}", toolDefinition.name(), context.getToolCallArguments());
            }

            @Override
            public void onStop(ToolCallingObservationContext context) {
                ToolDefinition toolDefinition = context.getToolDefinition();
                logger.info("ToolCalling done: {} - {}", toolDefinition.name(), context.getToolCallResult());
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
                logger.info("EmbeddingModelObservation start: {} - {}", context.getOperationMetadata().operationType(),
                        context.getOperationMetadata().provider());
            }
        };
    }


}
