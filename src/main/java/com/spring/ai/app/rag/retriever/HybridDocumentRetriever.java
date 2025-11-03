package com.spring.ai.app.rag.retriever;

import com.spring.ai.app.rag.model.Document;
import com.spring.ai.app.rag.model.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class HybridDocumentRetriever implements DocumentRetriever {
    private static final Logger logger = LoggerFactory.getLogger(HybridDocumentRetriever.class);

    private final DocumentRetriever bm25DocumentRetriever;
    private final DocumentRetriever vectorStoreDocumentRetriever;

    // 最终输出的 Top-K
    @Value("${spring.ai.retrieval.hybrid.top-k:${spring.ai.rag.hybrid.top-k:5}}")
    private int hybridTopK;

    // RRF 融合的平滑参数 k（越大越平滑）
    @Value("${spring.ai.retrieval.hybrid.rrf-k:${spring.ai.rag.rrf-k:60.0}}")
    private double rrfK;

    // 通道权重（用于按通道加权融合）
    @Value("${spring.ai.retrieval.hybrid.weight.bm25:${spring.ai.rag.hybrid.weight.bm25:1.0}}")
    private double bm25Weight;

    @Value("${spring.ai.retrieval.hybrid.weight.vector:${spring.ai.rag.hybrid.weight.vector:1.0}}")
    private double vectorWeight;

    public HybridDocumentRetriever(
            @Qualifier("bm25DocumentRetriever") DocumentRetriever bm25DocumentRetriever,
            @Qualifier("vectorStoreDocumentRetriever") DocumentRetriever vectorStoreDocumentRetriever) {
        this.bm25DocumentRetriever = bm25DocumentRetriever;
        this.vectorStoreDocumentRetriever = vectorStoreDocumentRetriever;
    }

    @Override
    public List<Document> retrieve(Query query) {
        // 打印实际执行的检索方式与关键参数
        try {
            logger.info("使用的检索方式: Hybrid(RRF融合) | topK={} | rrfK={}", hybridTopK, rrfK);
        } catch (Exception e) {
            logger.warn("打印Hybrid检索日志失败: {}", e.getMessage());
        }
        // 双通道召回（候选大小由各自 retriever 内部配置控制）
        List<Document> bm25Docs = safeList(this.bm25DocumentRetriever.retrieve(query));
        List<Document> vecDocs = safeList(this.vectorStoreDocumentRetriever.retrieve(query));

        try {
            logger.info("Hybrid子通道召回: bm25={} 条, vector={} 条", bm25Docs.size(), vecDocs.size());
        } catch (Exception e) {
            logger.warn("打印Hybrid子通道召回日志失败: {}", e.getMessage());
        }

        // 使用 RRF（Reciprocal Rank Fusion）融合，按排名位置计算分数，并按通道加权
        Map<String, FusionEntry> fused = new LinkedHashMap<>();
        applyRrf(fused, bm25Docs, "bm25");
        applyRrf(fused, vecDocs, "vec");

        // 排序并截断到 Top-K
        return fused.values().stream()
                    .sorted(Comparator.comparingDouble((FusionEntry e) -> e.score).reversed())
                    .limit(hybridTopK)
                    .map(e -> e.doc)
                    .collect(Collectors.toList());
    }

    @Override
    public List<List<Document>> retrieveBatch(List<Query> queries) {
        if (queries == null || queries.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            logger.info("使用的检索方式: Hybrid(RRF融合) 批量 | 批次数量={} | topK={} | rrfK={}",
                    queries.size(), hybridTopK, rrfK);
        } catch (Exception ignore) {}

        List<List<Document>> bm25Batches = this.bm25DocumentRetriever.retrieveBatch(queries);
        List<List<Document>> vecBatches = this.vectorStoreDocumentRetriever.retrieveBatch(queries);

        int size = queries.size();
        List<List<Document>> fusedOutputs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            List<Document> bm = safeList(i < bm25Batches.size() ? bm25Batches.get(i) : Collections.emptyList());
            List<Document> vc = safeList(i < vecBatches.size() ? vecBatches.get(i) : Collections.emptyList());
            Map<String, FusionEntry> fused = new LinkedHashMap<>();
            applyRrf(fused, bm, "bm25");
            applyRrf(fused, vc, "vec");
            List<Document> top = fused.values().stream()
                    .sorted(Comparator.comparingDouble((FusionEntry e) -> e.score).reversed())
                    .limit(hybridTopK)
                    .map(e -> e.doc)
                    .collect(Collectors.toList());
            fusedOutputs.add(top);
        }
        return fusedOutputs;
    }

    private void applyRrf(Map<String, FusionEntry> fused, List<Document> docs, String channel) {
        for (int i = 0; i < docs.size(); i++) {
            Document d = docs.get(i);
            String key = uniqueKeyFor(d);
            double rrfScore = 1.0 / (rrfK + (i + 1)); // rank 从 1 开始
            double weight = 1.0;
            if ("bm25".equalsIgnoreCase(channel)) {
                weight = bm25Weight;
            } else if ("vec".equalsIgnoreCase(channel) ) {
                weight = vectorWeight;
            }
            double fusedScore = weight * rrfScore;
            fused.compute(key, (k, entry) -> {
                if (entry == null) {
                    entry = new FusionEntry(d, 0.0);
                }
                entry.score += fusedScore;
                return entry;
            });
            try {
                logger.debug("RRF加分 channel={} key={} rank={} base={} weight={} fused={}", channel, key, (i + 1), rrfScore, weight, fusedScore);
            } catch (Exception ignore) {}
        }
    }

    private String uniqueKeyFor(Document doc) {
        return Optional.ofNullable(doc.metadata().get("id"))
                       .map(String::valueOf)
                       .orElseGet(() -> String.valueOf(doc.getText().hashCode()));
    }

    private List<Document> safeList(List<Document> list) {
        return list != null ? list : Collections.emptyList();
    }

    private static class FusionEntry {
        final Document doc;
        double score;

        FusionEntry(Document doc, double score) {
            this.doc = doc;
            this.score = score;
        }
    }
}
