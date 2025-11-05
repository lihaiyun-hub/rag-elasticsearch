package com.spring.ai.app.rag.retriever;

import com.spring.ai.app.rag.model.Document;
import com.spring.ai.app.rag.model.Query;
import com.spring.ai.app.rag.services.EmbeddingService;
import com.spring.ai.app.rag.vectorstore.ElasticsearchVectorStore;
import com.spring.ai.app.rag.vectorstore.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component("vectorStoreDocumentRetriever")
public class VectorStoreDocumentRetriever implements DocumentRetriever {
    private static final Logger logger = LoggerFactory.getLogger(VectorStoreDocumentRetriever.class);

    private final ElasticsearchVectorStore vectorStore;
    private final EmbeddingService embeddingService;

    @Value("${spring.ai.retrieval.vector.top-k:${spring.ai.rag.vector.top-k:5}}")
    private int topK;

    @Value("${spring.ai.retrieval.vector.similarity-threshold:${spring.ai.rag.vector.similarity-threshold:0.0}}")
    private double similarityThreshold;

    public VectorStoreDocumentRetriever(ElasticsearchVectorStore vectorStore, EmbeddingService embeddingService) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
    }

    @Override
    public List<Document> retrieve(Query query) {
        // 打印实际执行的检索方式与关键参数
        logger.info("使用的检索方式: 向量检索 | topK={} | threshold={}", topK, similarityThreshold);
        List<Float> queryEmbedding;
        Object precomputed = (query.getMetadata() != null) ? query.getMetadata().get("embedding") : null;
        if (precomputed instanceof List) {
            try {
                @SuppressWarnings("unchecked")
                List<Object> raw = (List<Object>) precomputed;
                List<Float> vec = new ArrayList<>(raw.size());
                for (Object o : raw) {
                    if (o instanceof Number) {
                        vec.add(((Number) o).floatValue());
                    } else if (o instanceof String) {
                        vec.add(Float.parseFloat((String) o));
                    }
                }
                queryEmbedding = vec;
                logger.debug("使用预置向量进行检索，维度: {}", queryEmbedding.size());
            } catch (Exception castEx) {
                logger.warn("读取预置向量失败，回退到实时嵌入: {}", castEx.getMessage());
                queryEmbedding = embeddingService.embed(query.getText());
            }
        } else {
            queryEmbedding = embeddingService.embed(query.getText());
        }
        // 从查询元数据中读取期望的 phase，交由 ES 层过滤（默认包含通用缺失字段）
        String desiredPhase = null;
        Object phaseObj = (query.getMetadata() != null) ? query.getMetadata().get("phase") : null;
        if (phaseObj instanceof String) desiredPhase = ((String) phaseObj).trim();
        List<SearchResult> results = vectorStore.search(
                queryEmbedding,
                topK,
                (similarityThreshold > 0.0) ? similarityThreshold : null,
                desiredPhase
        );
        List<SearchResult> filtered = new ArrayList<>();
        for (SearchResult r : results) {
            Object scoreObj = r.metadata() != null ? r.metadata().get("score") : null;
            double score = scoreObj instanceof Number ? ((Number) scoreObj).doubleValue() : Double.NaN;
            if (similarityThreshold <= 0.0 || (!Double.isNaN(score) && score >= similarityThreshold)) {
                filtered.add(r);
            }
        }
        try {
            logger.info("向量召回(ES含phase过滤): 原始={} | 阈值过滤后={}", results.size(), filtered.size());
        } catch (Exception ignore) {}
        List<Document> docs = new ArrayList<>();
        for (SearchResult r : filtered) {
            docs.add(Document.builder()
                    .content(r.text())
                    .metadata(r.metadata())
                    .build());
        }
        return docs;
    }

    @Override
    public List<List<Document>> retrieveBatch(List<Query> queries) {
        if (queries == null || queries.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        logger.info("使用的检索方式: 向量批量检索 | 批次数量={} | topK={} | threshold={} ",
                queries.size(), topK, similarityThreshold);

        // 读取预置向量；若不存在则批量嵌入
        List<List<Float>> embeddings = new ArrayList<>(queries.size());
        boolean allPrecomputed = true;
        for (Query q : queries) {
            Object pre = (q.getMetadata() != null) ? q.getMetadata().get("embedding") : null;
            if (pre instanceof java.util.List) {
                try {
                    @SuppressWarnings("unchecked")
                    List<Object> raw = (List<Object>) pre;
                    List<Float> vec = new ArrayList<>(raw.size());
                    for (Object o : raw) {
                        if (o instanceof Number) vec.add(((Number) o).floatValue());
                        else if (o instanceof String) vec.add(Float.parseFloat((String) o));
                    }
                    embeddings.add(vec);
                } catch (Exception ex) {
                    allPrecomputed = false;
                    break;
                }
            } else {
                allPrecomputed = false;
                break;
            }
        }
        if (!allPrecomputed) {
            // 批量嵌入文本
            List<String> texts = new ArrayList<>(queries.size());
            for (Query q : queries) texts.add(q.getText());
            logger.info("批量向量检索原始 query 列表: {}", texts);
            embeddings = embeddingService.embedBatch(texts);
        }

        // 通过 msearch 批量检索
        Double threshold = (similarityThreshold > 0.0) ? similarityThreshold : null;
        List<SearchResult> tmp; // just for type hint
        // 为每个查询读取期望的 phase，交由 ES 层过滤
        List<String> phases = new ArrayList<>(queries.size());
        for (Query q : queries) {
            Object po = (q.getMetadata() != null) ? q.getMetadata().get("phase") : null;
            phases.add(po instanceof String ? ((String) po).trim() : null);
        }
        List<List<SearchResult>> results =
                vectorStore.multiKnnSearch(embeddings, topK, threshold, phases);

        List<List<Document>> out = new ArrayList<>(results.size());
        for (List<SearchResult> rlist : results) {
            List<Document> docs = new ArrayList<>();
            for (SearchResult r : rlist) {
                docs.add(Document.builder().content(r.text()).metadata(r.metadata()).build());
            }
            out.add(docs);
        }
        return out;
    }
}
