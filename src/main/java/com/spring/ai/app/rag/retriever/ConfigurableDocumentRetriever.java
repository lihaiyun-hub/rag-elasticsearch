package com.spring.ai.app.rag.retriever;

import com.spring.ai.app.rag.model.Document;
import com.spring.ai.app.rag.model.Message;
import com.spring.ai.app.rag.model.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 根据配置动态切换底层检索方式的统一入口
 */
@Component("primaryDocumentRetriever")
public class ConfigurableDocumentRetriever implements DocumentRetriever {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurableDocumentRetriever.class);

    private final Map<String, DocumentRetriever> strategyMap = new ConcurrentHashMap<>();
    private final String currentType;

    public ConfigurableDocumentRetriever(@Qualifier("bm25DocumentRetriever") DocumentRetriever bm25,
                                         @Qualifier("vectorStoreDocumentRetriever") DocumentRetriever vector,
                                         @Qualifier("hybridDocumentRetriever") DocumentRetriever hybrid,
                                         @Value("${spring.ai.rag.retriever.type:${spring.rag.retriever.type:hybrid}}") String type) {
        strategyMap.put("bm25", bm25);
        strategyMap.put("vector", vector);
        strategyMap.put("hybrid", hybrid);
        this.currentType = type;
    }

    @Override
    public List<Document> retrieve(Query query) {
        DocumentRetriever retriever = strategyMap.get(currentType);
        if (retriever == null) {
            retriever = strategyMap.get("hybrid");
        }
        // 在实际执行前打印真正使用的检索方式（以运行时实例为准）

        String retrieverName = retriever.getClass().getSimpleName();
        logger.info("实际执行的检索器: {}", retrieverName);

        // 如果 metadata 中带有历史记录，则在检索层面完成“增强查询”的双检索与合并
        List<Message> history = null;
        try {
            Object h = query.getMetadata() != null ? query.getMetadata().get("history") : null;
            if (h instanceof List) {
                @SuppressWarnings("unchecked")
                List<Message> casted = (List<Message>) h;
                history = casted;
            }
        } catch (Exception ignore) {
        }

        // 统一走批量检索：始终构造请求列表（含原始query，若有历史则附加增强query）
        Query q1 = Query.builder().text("最新提问[" + query.getText() + "]").chatId(query.getChatId()).metadata(query.getMetadata()).build();
        List<Query> requestQueries = new ArrayList<>();
        requestQueries.add(q1);
        if (history != null && !history.isEmpty()) {
            String augmented = buildAugmentedQuery(query.getText(), history, 2);
            Map<String, Object> md2 = (query.getMetadata() != null) ? new HashMap<>(query.getMetadata()) : new HashMap<>();
            md2.put("augmented", true);
            Query q2 = Query.builder().text(augmented).chatId(query.getChatId()).metadata(md2).build();
            requestQueries.add(q2);
            logger.info("最新提问：{}", query.getText());
            logger.info("增强提问：{}", augmented);
        } 
        List<List<Document>> batches = retriever.retrieveBatch(requestQueries);
        if (batches.isEmpty()) {
            return Collections.emptyList();
        }
        if (batches.size() == 1) {
            return batches.get(0);
        }
        List<Document> docsA = batches.get(0);
        List<Document> docsB = batches.size() > 1 ? batches.get(1) : Collections.emptyList();
        return mergeAndDedupDocuments(docsA, docsB);
    }

    @Override
    public List<List<Document>> retrieveBatch(List<Query> queries) {
        DocumentRetriever retriever = strategyMap.get(currentType);
        if (retriever == null) {
            retriever = strategyMap.get("hybrid");
        }
        try {
            String retrieverName = retriever.getClass().getSimpleName();
            logger.info("批量检索执行的检索器: {} | 批次数量={} ", retrieverName, queries != null ? queries.size() : 0);
        } catch (Exception e) {
            logger.warn("打印批量检索器名称失败: {}", e.getMessage());
        }
        return retriever.retrieveBatch(queries);
    }

    /**
     * 构建增强查询：将当前query与最近N轮的对话上下文拼接
     */
    private String buildAugmentedQuery(String currentQuery, List<Message> history, int lastTurns) {
        try {
            if (lastTurns <= 0 || history == null || history.isEmpty()) {
                return currentQuery;
            }
            // 仅保留非SYSTEM消息
            List<Message> nonSystem = history.stream()
                    .filter(m -> m.getType() != Message.Type.SYSTEM)
                    .collect(Collectors.toList());
            if (nonSystem.isEmpty()) {
                return currentQuery;
            }
            int take = Math.min(nonSystem.size(), lastTurns * 2);
            List<Message> tail = new ArrayList<>(nonSystem.subList(nonSystem.size() - take, nonSystem.size()));
            // 拼接为：历史对话[user:xxx;assistant:yyy;]最新提问[currentQuery]
            String historyPart = tail.stream()
                    .map(m -> {
                        String role = (m.getType() == Message.Type.USER) ? "user" : "assistant";
                        String content = (m.getContent() == null ? "" : m.getContent());
                        return role + ":" + content;
                    })
                    .collect(Collectors.joining(";"));

            return "历史对话[" + historyPart + "]最新提问[" + currentQuery + "]";
        } catch (Exception e) {
            logger.warn("构建增强查询失败，退回原始query: {}", e.getMessage());
            return currentQuery;
        }
    }

    /**
     * 合并并去重两个文档列表，保留第一个列表的顺序优先
     */
    private List<Document> mergeAndDedupDocuments(List<Document> first, List<Document> second) {
        List<Document> a = first != null ? first : Collections.emptyList();
        List<Document> b = second != null ? second : Collections.emptyList();
        LinkedHashMap<String, Document> map = new LinkedHashMap<>();
        for (Document d : a) {
            String key = uniqueKeyFor(d);
            map.putIfAbsent(key, d);
        }
        for (Document d : b) {
            String key = uniqueKeyFor(d);
            map.putIfAbsent(key, d);
        }
        return new ArrayList<>(map.values());
    }

    private String uniqueKeyFor(Document doc) {
        Object id = (doc.metadata() != null) ? doc.metadata().get("id") : null;
        if (id != null) return String.valueOf(id);
        String text = doc.getText();
        return String.valueOf(text != null ? text.hashCode() : 0);
    }


}
