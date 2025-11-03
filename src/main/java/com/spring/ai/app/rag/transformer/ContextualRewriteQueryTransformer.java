package com.spring.ai.app.rag.transformer;

import com.spring.ai.app.rag.chat.ChatMemory;
import com.spring.ai.app.rag.model.Message;
import com.spring.ai.app.rag.model.Query;
import com.spring.ai.app.rag.services.LargeLanguageModelService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ContextualRewriteQueryTransformer implements QueryTransformer {

    private final LargeLanguageModelService llmService;
    private final ChatMemory chatMemory;
    private final String promptTemplate;

    public ContextualRewriteQueryTransformer(LargeLanguageModelService llmService,
                                           ChatMemory chatMemory,
                                           Resource customPromptResource) throws IOException {
        this.llmService = llmService;
        this.chatMemory = chatMemory;
        // 复用 PromptConfig 已初始化的模板资源
        this.promptTemplate = customPromptResource.getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public Query transform(Query query) {
        List<Message> history = chatMemory.get(query.getChatId());
        if (history == null || history.isEmpty()) {
            return query;
        }

        String prompt = renderTemplate(history, query.getText());
        String rewritten = llmService.chat(prompt);

        return Query.builder()
                .text(rewritten)
                .chatId(query.getChatId())
                .metadata(query.getMetadata())
                .build();
    }

    /**
     * 仅负责把变量塞进模板，不再硬编码任何提示词
     */
    private String renderTemplate(List<Message> history, String currentQuery) {
        String historyText = history.stream()
                .map(m -> {
                    String role = switch (m.getType()) {
                        case SYSTEM -> "system";
                        case USER -> "user";
                        case ASSISTANT -> "assistant";
                    };
                    return String.format("%s: %s", role, m.getContent());
                })
                .collect(Collectors.joining("\n"));

        return promptTemplate
                .replace("{history}", historyText)
                .replace("{query}", currentQuery);
    }
}