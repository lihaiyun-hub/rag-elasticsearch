package com.spring.ai.app.rag.transformer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;

public class ContextualRewriteQueryTransformer implements QueryTransformer {

    private static final Logger logger = LoggerFactory.getLogger(ContextualRewriteQueryTransformer.class);
    private static final ThreadLocal<String> CURRENT_CHAT_ID = new ThreadLocal<>();
    
    private final ChatClient chatClient;
    private final PromptTemplate promptTemplate;
    private final ChatMemory chatMemory;

    /**
     * 设置当前线程的chatId，用于QueryTransformer获取历史记录
     */
    public static void setCurrentChatId(String chatId) {
        CURRENT_CHAT_ID.set(chatId);
    }

    /**
     * 清除当前线程的chatId
     */
    public static void clearCurrentChatId() {
        CURRENT_CHAT_ID.remove();
    }
    /**
     * 获取当前线程的chatId
     */
    private static String getCurrentChatId() {
        return CURRENT_CHAT_ID.get();
    }

    public ContextualRewriteQueryTransformer(ChatClient.Builder chatClientBuilder, Resource customPromptResource, ChatMemory chatMemory) {
        this.chatClient = chatClientBuilder.build();
        this.chatMemory = chatMemory;
        
        // 加载提示模板
        Resource promptResource = customPromptResource != null ? customPromptResource :
                new DefaultResourceLoader().getResource("classpath:prompts/contextual-rewrite-prompt.st");
        
        this.promptTemplate = PromptTemplate.builder()
                .resource(promptResource)
                .build();
    }

    @Override
    public Query transform(Query query) {
        String queryText = query.text();
        
        if (queryText == null || queryText.trim().isEmpty()) {
            return query;
        }
        // 尝试从ThreadLocal获取chatId
        String chatId = getCurrentChatId();
        String history = "";
        String currentQuery = queryText;

        // 如果有chatId，直接从ChatMemory获取历史记录
        if (chatId != null && chatMemory != null) {
            List<Message> messages = chatMemory.get(chatId);
            if (!messages.isEmpty()) {
                StringBuilder historyBuilder = new StringBuilder();
                for (Message message : messages) {
                    historyBuilder.append(message.getMessageType())
                            .append(": ")
                            .append(message.getText())
                            .append("\n");
                }
                history = historyBuilder.toString();
            }
        }
        String transformedQuery;
        // 1. 简单规则处理
        String processedQuery = handleSimpleQueries(currentQuery);
        if (!processedQuery.equals(currentQuery)) {
            transformedQuery = processedQuery;
        } else {
             // 3. 上下文感知的查询重写（历史记录可以为空）
            try {
                transformedQuery = rewriteQueryWithContext(history, currentQuery);
            } catch (Exception e) {
                transformedQuery = currentQuery;
            }
        }
        // 关键：确保返回的查询是纯净的，不包含任何内部格式标记
        return Query.builder().text(transformedQuery).build();
    }
    
    /**
     * 处理简单的问候语和常见查询，避免被错误重写
     */
    private String handleSimpleQueries(String query) {
        String cleanQuery = query.trim().toLowerCase();
        
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
     * 使用LLM进行上下文感知的查询重写
     */
    private String rewriteQueryWithContext(String history, String currentQuery) {
        String prompt = promptTemplate.render(Map.of(
                "history", history,
                "query", currentQuery
        ));
        ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
        return response.getResult().getOutput().getText().trim();
    }

}