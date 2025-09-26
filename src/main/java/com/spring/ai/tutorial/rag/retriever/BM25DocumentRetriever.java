package com.spring.ai.tutorial.rag.retriever;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component("BM25DocumentRetriever")
public class BM25DocumentRetriever implements DocumentRetriever {

    private final ElasticsearchClient elasticsearchClient;

    @Value("${spring.ai.vectorstore.elasticsearch.index-name}")
    private String indexName;

    // 可配置的 BM25 候选集大小（Top-N）
    @Value("${spring.ai.rag.bm25.top-n}")
    private int bm25TopN;

    public BM25DocumentRetriever(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public List<Document> retrieve(Query query) {
        try {
            SearchRequest searchRequest = new SearchRequest.Builder()
                    .index(indexName)
                    .query(q -> q
                            .match(m -> m
                                    // Spring AI ElasticsearchVectorStore 默认文本字段为 "content"
                                    .field("content")
                                    .query(query.text())
                            )
                    )
                    // 使用 Elasticsearch API 的 size 正确限制结果数量
                    .size(bm25TopN)
                    .build();

            SearchResponse<Document> searchResponse = elasticsearchClient.search(searchRequest, Document.class);

            return searchResponse.hits().hits().stream()
                    .map(Hit::source)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}