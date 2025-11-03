package com.spring.ai.app.rag.retriever;

import com.spring.ai.app.rag.model.Document;
import com.spring.ai.app.rag.model.Query;
import com.spring.ai.app.rag.vectorstore.ElasticsearchVectorStore;
import com.spring.ai.app.rag.vectorstore.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component("bm25DocumentRetriever")
public class BM25DocumentRetriever implements DocumentRetriever {
    private static final Logger logger = LoggerFactory.getLogger(BM25DocumentRetriever.class);

    private final ElasticsearchVectorStore vectorStore;

    @Value("${spring.ai.retrieval.bm25.top-n:${spring.ai.rag.bm25.top-n:5}}")
    private int topN;

    @Value("${spring.ai.retrieval.bm25.boost:${spring.ai.rag.bm25.boost:1.0}}")
    private float boost;

    public BM25DocumentRetriever(ElasticsearchVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public List<Document> retrieve(Query query) {
        // 打印实际执行的检索方式与关键参数
        try {
            logger.info("使用的检索方式: BM25 | topN={} | boost={}", topN, boost);
        } catch (Exception e) {
            logger.warn("打印BM25检索日志失败: {}", e.getMessage());
        }
        // 使用真正的BM25检索
        List<SearchResult> results = vectorStore.bm25Search(query.getText(), topN, boost);
        List<Document> docs = new ArrayList<>();
        for (SearchResult r : results) {
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
        try {
            logger.info("使用的检索方式: BM25 批量检索 | 批次数量={} | topN={} | boost={} ",
                    queries.size(), topN, boost);
        } catch (Exception ignore) {}

        java.util.List<String> texts = new java.util.ArrayList<>(queries.size());
        for (Query q : queries) {
            texts.add(q.getText());
        }
        java.util.List<java.util.List<com.spring.ai.app.rag.vectorstore.SearchResult>> results =
                vectorStore.bm25MultiSearch(texts, topN, boost);
        java.util.List<java.util.List<Document>> out = new java.util.ArrayList<>(results.size());
        for (java.util.List<com.spring.ai.app.rag.vectorstore.SearchResult> rlist : results) {
            java.util.List<Document> docs = new java.util.ArrayList<>();
            for (com.spring.ai.app.rag.vectorstore.SearchResult r : rlist) {
                docs.add(Document.builder().content(r.text()).metadata(r.metadata()).build());
            }
            out.add(docs);
        }
        return out;
    }
}
