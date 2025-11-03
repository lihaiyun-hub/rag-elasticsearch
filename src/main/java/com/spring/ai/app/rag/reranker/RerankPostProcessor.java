package com.spring.ai.app.rag.reranker;

import com.spring.ai.app.rag.model.Document;
import com.spring.ai.app.rag.model.Query;
import com.spring.ai.app.rag.model.Message;
import com.spring.ai.app.rag.services.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Order(100)
@Component
public class RerankPostProcessor {
    private static final Logger logger = LoggerFactory.getLogger(RerankPostProcessor.class);


    private final RerankService rerankService;

    public RerankPostProcessor(RerankService rerankService) {
        this.rerankService = rerankService;
    }

    public List<Document> process(Query query, List<Document> documents) {
        if (documents.isEmpty() || !rerankService.isEnabled()) {
            return documents;
        }
        List<String> texts = documents.stream().map(Document::getText).collect(Collectors.toList());
        logger.info("输入到重排序模型的文本 {} 条文档", texts);

        // 1) 使用最新提问进行重排序
        List<RerankService.ResultItem> latest = rerankService.rerank(query.getText(), texts);

        // 2) 若存在历史，构造增强查询并进行第二次重排序
        List<Message> history = null;
        try {
            Object h = query.getMetadata() != null ? query.getMetadata().get("history") : null;
            if (h instanceof List) {
                @SuppressWarnings("unchecked")
                List<Message> casted = (List<Message>) h;
                history = casted;
            }
        } catch (Exception ignore) {}

        List<RerankService.ResultItem> augmented = Collections.emptyList();
        String augmentedQuery = null;
        if (history != null && !history.isEmpty()) {
            augmentedQuery = buildAugmentedQuery(query.getText(), history, 2);
            augmented = rerankService.rerank(augmentedQuery, texts);
        }

        if ((latest == null || latest.isEmpty()) && (augmented == null || augmented.isEmpty())) {
            return documents;
        }

        // 3) 融合两路重排序分数（简单加权，默认各占50%）
        double wLatest = 0.5;
        double wAug = 0.5;
        Map<Integer, Double> finalScores = new HashMap<>();
        Map<Integer, Double> latestScores = new HashMap<>();
        Map<Integer, Double> augmentedScores = new HashMap<>();
        List<RerankService.ResultItem> latestSafe = (latest != null) ? latest : Collections.<RerankService.ResultItem>emptyList();
        for (RerankService.ResultItem r : latestSafe) {
            latestScores.put(r.index, r.relevanceScore);
        }
        List<RerankService.ResultItem> augmentedSafe = (augmented != null) ? augmented : Collections.<RerankService.ResultItem>emptyList();
        for (RerankService.ResultItem r : augmentedSafe) {
            augmentedScores.put(r.index, r.relevanceScore);
        }
        for (int i = 0; i < texts.size(); i++) {
            double l = latestScores.getOrDefault(i, 0.0);
            double a = augmentedScores.getOrDefault(i, 0.0);
            finalScores.put(i, wLatest * l + wAug * a);
        }
        List<Integer> order = new ArrayList<>(finalScores.keySet());
        order.sort((i1, i2) -> Double.compare(finalScores.get(i2), finalScores.get(i1)));
        try {
            String map1 = (latest != null ? latest : Collections.<RerankService.ResultItem>emptyList())
                    .stream().map(r -> "L#" + r.index + ":" + String.format("%.4f", r.relevanceScore))
                    .collect(Collectors.joining(", "));
            String map2 = (augmented != null ? augmented : Collections.<RerankService.ResultItem>emptyList())
                    .stream().map(r -> "A#" + r.index + ":" + String.format("%.4f", r.relevanceScore))
                    .collect(Collectors.joining(", "));
            String mapF = order.stream().map(i -> "F#" + i + ":" + String.format("%.4f", finalScores.get(i))).collect(Collectors.joining(", "));
            logger.info("重排序(L最新/A增强/F融合): {} | {} | {}", map1, map2, mapF);
        } catch (Exception ignore) {}

        List<Document> reordered = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (Integer idx : order) {
            if (idx >= 0 && idx < documents.size() && used.add(idx)) {
                Document doc = documents.get(idx);
                try {
                    Map<String, Object> md = doc.metadata();
                    if (md != null) {
                        md.put("rerank_latest", latestScores.getOrDefault(idx, 0.0));
                        if (augmentedQuery != null) {
                            md.put("rerank_augmented", augmentedScores.getOrDefault(idx, 0.0));
                        }
                        md.put("rerank_score", finalScores.get(idx));
                        md.put("rerank_rank", reordered.size() + 1);
                    }
                } catch (Exception ignore) {}
                reordered.add(doc);
            }
        }
        for (int i = 0; i < documents.size(); i++) {
            if (!used.contains(i)) {
                Document doc = documents.get(i);
                try {
                    Map<String, Object> md = doc.metadata();
                    if (md != null) {
                        md.put("rerank_latest", latestScores.getOrDefault(i, 0.0));
                        if (augmentedQuery != null) {
                            md.put("rerank_augmented", augmentedScores.getOrDefault(i, 0.0));
                        }
                        md.put("rerank_score", finalScores.getOrDefault(i, 0.0));
                        md.put("rerank_rank", reordered.size() + 1);
                    }
                } catch (Exception ignore) {}
                reordered.add(doc);
            }
        }
        return reordered;
    }

    private String buildAugmentedQuery(String currentQuery, List<Message> history, int lastTurns) {
        try {
            if (lastTurns <= 0 || history == null || history.isEmpty()) {
                return currentQuery;
            }
            List<Message> nonSystem = history.stream()
                    .filter(m -> m.getType() != Message.Type.SYSTEM)
                    .collect(Collectors.toList());
            if (nonSystem.isEmpty()) {
                return currentQuery;
            }
            int take = Math.min(nonSystem.size(), lastTurns * 2);
            List<Message> tail = new ArrayList<>(nonSystem.subList(nonSystem.size() - take, nonSystem.size()));
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
}
