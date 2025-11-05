package com.spring.ai.app.rag.vectorstore;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.MsearchRequest;
import co.elastic.clients.elasticsearch.core.MsearchResponse;
import co.elastic.clients.elasticsearch.core.msearch.MultiSearchResponseItem;
import co.elastic.clients.elasticsearch.core.msearch.MultiSearchResult;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;

import com.spring.ai.app.rag.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import java.util.Map;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Elasticsearch向量存储实现
 * 支持文档存储、向量相似度搜索和文档检索功能
 */
public class ElasticsearchVectorStore {
    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchVectorStore.class);

    private final ElasticsearchClient elasticsearchClient;
    private final String indexName;
    private final int dimensions;
    private final String similarity;

    public ElasticsearchVectorStore(ElasticsearchClient elasticsearchClient,
                                  String indexName,
                                  int dimensions,
                                  String similarity) {
        this.elasticsearchClient = elasticsearchClient;
        this.indexName = indexName;
        this.dimensions = dimensions;
        this.similarity = similarity;
        
        // 初始化索引
        initializeSchema();
    }

    /**
     * 初始化索引schema：完全使用 Elasticsearch Java API，不再拼接 JSON 字符串
     */
    private void initializeSchema() {
        try {
            boolean exists = elasticsearchClient.indices()
                    .exists(req -> req.index(indexName))
                    .value();
            if (exists) {
                return;
            }
            // 先检测 IK 是否可用，不可用则回退到 standard 分词
            boolean ikAvailable = false;
            try {
                elasticsearchClient.indices().analyze(a -> a
                        .analyzer("ik_max_word")
                        .text("IK analyzer probe"));
                ikAvailable = true;
            } catch (Exception ignore) {
                ikAvailable = false;
            }

            logger.info("Creating index {} | IK available: {}", indexName, ikAvailable);

            // 1. settings（用 Map 兼容当前 ES client 版本）
            Map<String, Object> settings;
            if (ikAvailable) {
                settings = Map.of(
                        "number_of_shards", 1,
                        "number_of_replicas", 1,
                        "analysis", Map.of(
                                "analyzer", Map.of(
                                        "ik_analyzer", Map.of(
                                                "type", "custom",
                                                "tokenizer", "ik_max_word"))));
            } else {
                // 无 IK 插件，使用 standard 分词，不声明 analysis 配置避免未知 tokenizer 报错
                settings = Map.of(
                        "number_of_shards", 1,
                        "number_of_replicas", 1);
            }

            // 2. mapping（同样用 Map 写法，避免 TypeMapping/Property 版本差异）
            Map<String, Object> textField;
            if (ikAvailable) {
                textField = Map.of(
                        "type", "text",
                        "analyzer", "ik_analyzer",
                        "search_analyzer", "ik_smart",
                        "similarity", "BM25",
                        "fields", Map.of(
                                "keyword", Map.of("type", "keyword", "ignore_above", 256),
                                "bm25", Map.of(
                                        "type", "text",
                                        "analyzer", "ik_analyzer",
                                        "search_analyzer", "ik_smart",
                                        "similarity", "BM25"))
                );
            } else {
                textField = Map.of(
                        "type", "text",
                        "analyzer", "standard",
                        "search_analyzer", "standard",
                        "similarity", "BM25",
                        "fields", Map.of(
                                "keyword", Map.of("type", "keyword", "ignore_above", 256),
                                "bm25", Map.of(
                                        "type", "text",
                                        "analyzer", "standard",
                                        "search_analyzer", "standard",
                                        "similarity", "BM25"))
                );
            }

            Map<String, Object> mapping = Map.of(
                    "properties", Map.of(
                            "text", textField,
                            "embedding", Map.of(
                                    "type", "dense_vector",
                                    "dims", dimensions,
                                    // 为 KNN 启用索引并保留相似度配置
                                    "index", true,
                                    "similarity", similarity),
                            "metadata", Map.of("type", "object")));

            // 3. 创建索引（用 withJson 直接灌 Map，兼容所有版本）
            String body;
            try {
                body = new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(Map.of("settings", settings, "mappings", mapping));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new RuntimeException("build index json failed", e);
            }
            CreateIndexRequest request = CreateIndexRequest.of(req -> req
                    .index(indexName)
                    .withJson(new java.io.StringReader(body)));

            CreateIndexResponse response = elasticsearchClient.indices().create(request);
            if (!response.acknowledged()) {
                throw new RuntimeException("Failed to create index: " + indexName);
            }
            logger.info("Successfully created index {} (analyzer: {})", indexName, ikAvailable ? "ik" : "standard");
        } catch (IOException e) {
            // 补充可读信息，帮助快速定位失败原因（如 IK 不存在、权限不足、版本不兼容等）
            String hint = "" +
                    " | Hints: check ES connectivity/credentials, IK plugin availability, " +
                    "and mapping compatibility (dense_vector).";
            throw new RuntimeException("Failed to initialize Elasticsearch schema: " + e.getMessage() + hint, e);
        }
    }



    /**
     * 批量添加文档
     */
    public void addDocuments(List<Document> documents, List<List<Float>> embeddings) {
        if (documents == null || embeddings == null || documents.size() != embeddings.size()) {
            throw new IllegalArgumentException("documents 与 embeddings 数量必须一致");
        }
        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder().index(indexName);
            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                List<Float> emb = embeddings.get(i);
                Map<String, Object> source = new HashMap<>();
                source.put("text", doc.getText());
                source.put("embedding", emb);
                if (doc.metadata() != null) source.put("metadata", doc.metadata());
                String id = computeStableId(doc);
                bulkBuilder.operations(op -> op.index(idx -> idx.id(id).document(source)));
            }
            BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());
            if (response.errors()) {
                throw new RuntimeException("批量索引部分失败: " + response.items().stream()
                    .filter(item -> item.error() != null)
                    .map(item -> item.error().reason())
                    .toList());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to add documents to vector store", e);
        }
    }

    /**
     * 基于文档内容生成稳定 ID，保证幂等导入：
     * 使用 text + source + sheet_name 组合后做 SHA-1。
     */
    private String computeStableId(Document doc) {
        String text = doc.getText() == null ? "" : doc.getText();
        String source = "";
        String sheet = "";
        Map<String, Object> meta = doc.metadata();
        if (meta != null) {
            Object s = meta.get("source");
            Object sh = meta.get("sheet_name");
            source = s == null ? "" : String.valueOf(s);
            sheet = sh == null ? "" : String.valueOf(sh);
        }
        String key = text + "|" + source + "|" + sheet;
        return sha1Hex(key);
    }

    private String sha1Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Integer.toHexString((b & 0xFF) | 0x100).substring(1));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not available", e);
        }
    }

    /**
     * KNN 相似度搜索（支持最小分数过滤）
     */
    public List<SearchResult> search(List<Float> queryEmbedding, int k) {
        // 保持兼容旧签名，默认不设置最小分数
        return search(queryEmbedding, k, null);
    }

    /**
     * KNN 相似度搜索（支持 minScore 服务端过滤）
     * @param queryEmbedding 查询向量
     * @param k              返回候选数
     * @param minScore       最小分数阈值（可为空；为空或 <=0 时不启用）
     */
    public List<SearchResult> search(List<Float> queryEmbedding, int k, Double minScore) {
        try {
            var builder = new SearchRequest.Builder();
            builder.index(indexName);
            builder.knn(knn -> knn
                .field("embedding")
                .queryVector(queryEmbedding)
                .k(k)
                .numCandidates(Math.max(k * 2, k + 10))
            );
            if (minScore != null && minScore > 0.0) {
                // 将阈值下推到 ES，减少低分命中返回
                builder.minScore(minScore);
            }
            SearchRequest request = builder.build();

            SearchResponse<Map> response = elasticsearchClient.search(request, Map.class);
            List<SearchResult> results = new ArrayList<>();
            for (var hit : response.hits().hits()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> source = hit.source();
                if (source == null) continue;
                String text = (String) source.get("text");
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) source.get("metadata");
                if (metadata == null) metadata = new HashMap<>();
                metadata.put("score", hit.score());
                metadata.put("id", hit.id());
                results.add(new SearchResult(text, metadata));
            }
            return results;
        } catch (IOException e) {
            logger.error("KNN search IO error", e);
            throw new RuntimeException("Failed to perform KNN search: " + e.getMessage(), e);
        }
    }

    /**
     * KNN 相似度搜索（支持 minScore 服务端过滤 + phase 过滤）
     */
    public List<SearchResult> search(List<Float> queryEmbedding, int k, Double minScore, String phase) {
        try {
            var builder = new SearchRequest.Builder();
            builder.index(indexName);
            // 使用 knn.filter 将 phase 过滤下推到 ES
            co.elastic.clients.elasticsearch._types.query_dsl.Query phaseQuery = buildPhaseFilterQuery(phase);
            var knnBuilder = new co.elastic.clients.elasticsearch._types.KnnQuery.Builder()
                    .field("embedding")
                    .queryVector(queryEmbedding)
                    .k(k)
                    .numCandidates(Math.max(k * 2, k + 10));
            if (phaseQuery != null) {
                knnBuilder.filter(phaseQuery);
            }
            builder.knn(knnBuilder.build());
            if (minScore != null && minScore > 0.0) {
                builder.minScore(minScore);
            }
            SearchRequest request = builder.build();

            SearchResponse<Map> response = elasticsearchClient.search(request, Map.class);
            List<SearchResult> results = new ArrayList<>();
            for (var hit : response.hits().hits()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> source = hit.source();
                if (source == null) continue;
                String text = (String) source.get("text");
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) source.get("metadata");
                if (metadata == null) metadata = new HashMap<>();
                metadata.put("score", hit.score());
                metadata.put("id", hit.id());
                results.add(new SearchResult(text, metadata));
            }
            return results;
        } catch (IOException e) {
            logger.error("KNN search (with phase) IO error", e);
            throw new RuntimeException("Failed to perform KNN search with phase: " + e.getMessage(), e);
        }
    }

    // 删除重复的内部接口定义，类已提供所需方法

    /**
     * BM25 检索（多词项 bool should）
     * @param query  查询文本（会被IK分词）
     * @param topK   返回条数
     * @param boost  BM25字段权重提升
     * @return 按BM25评分倒序的文档列表
     */
    public List<SearchResult> bm25Search(String query, int topK, float boost) {
        try {
            // 兼容两种字段：优先 text.bm25（若存在），同时回退到 text
            var searchRequest = SearchRequest.of(b -> b
                    .index(indexName)
                    .size(topK)
                    .query(q -> q
                            .bool(bool -> bool
                                    .should(sh -> sh
                                            .match(m -> m
                                                    .field("text")
                                                    .query(query)
                                                    .boost(boost))
                                    )
                                    .should(sh -> sh
                                            .match(m -> m
                                                    .field("text.bm25")
                                                    .query(query)
                                                    .boost(boost))
                                    )
                            )
                    )
            );

            var response = elasticsearchClient.search(searchRequest, Map.class);
            return response.hits().hits().stream()
                    .map(hit -> {
                        Map<String, Object> source = hit.source();
                        String text = (String) source.get("text");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> metadata = (Map<String, Object>) source.get("metadata");
                        if (metadata == null) metadata = new HashMap<>();
                        metadata.put("score", hit.score());
                        metadata.put("id", hit.id());
                        return new SearchResult(text, metadata);
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("BM25 search failed", e);
        }
    }


    /**
     * 批量 KNN 检索（使用 _msearch 以减少网络往返）
     * @param queryEmbeddings 向量列表
     * @param k 返回候选数
     * @param minScore 最小分数，可为空
     * @return 每个向量的检索结果列表，顺序与输入一致
     */
    // 已移除仅用于日志打印的批量 KNN 重载方法，保留原有签名与实现

    public List<List<SearchResult>> multiKnnSearch(List<List<Float>> queryEmbeddings, int k, Double minScore) {
        if (queryEmbeddings == null || queryEmbeddings.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        // 首选：使用“类型化构建”方式为 _msearch 逐项添加 searches(...)
        try {
            int numCandidates = Math.max(k * 2, k + 10);
            java.util.List<co.elastic.clients.elasticsearch.core.msearch.RequestItem> items = new java.util.ArrayList<>(queryEmbeddings.size());
            for (List<Float> vec : queryEmbeddings) {
                co.elastic.clients.elasticsearch.core.msearch.RequestItem item =
                        co.elastic.clients.elasticsearch.core.msearch.RequestItem.of(ri -> ri
                                .header(h -> h.index(indexName))
                                .body(b -> b
                                        .size(k)
                                        .knn(q -> q
                                                .field("embedding")
                                                .queryVector(vec)
                                                .k(k)
                                                .numCandidates(numCandidates)
                                        )
                                        .minScore((minScore != null && minScore > 0.0) ? minScore : null)
                                )
                        );
                items.add(item);
            }

            MsearchRequest request = MsearchRequest.of(b -> b
                    .index(indexName)
                    .searches(items)
            );

            MsearchResponse<Map> response = elasticsearchClient.msearch(request, Map.class);

            List<List<SearchResult>> all = new ArrayList<>();
            for (MultiSearchResponseItem<Map> item : response.responses()) {
                List<SearchResult> results = new ArrayList<>();
                if (item != null && item.result() != null && item.result().hits() != null) {
                    for (Hit<Map> hit : item.result().hits().hits()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> source = hit.source();
                        if (source == null) continue;
                        String text = (String) source.get("text");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> metadata = (Map<String, Object>) source.get("metadata");
                        if (metadata == null) metadata = new java.util.HashMap<>();
                        metadata.put("score", hit.score());
                        metadata.put("id", hit.id());
                        results.add(new SearchResult(text, metadata));
                    }
                }
                all.add(results);
            }
            return all;
        } catch (Exception e) {
            // 在少数环境下，类型化 msearch 也可能由于映射/版本不兼容而失败（例如 IK 或 dense_vector 配置问题）。
            // 为确保功能可用，这里降级为逐条 search 的安全回退策略。
            logger.warn("类型化 _msearch 失败，降级为逐条 KNN 检索: {}", e.getMessage());
            List<List<SearchResult>> fallback = new ArrayList<>(queryEmbeddings.size());
            for (List<Float> vec : queryEmbeddings) {
                try {
                    List<SearchResult> one = this.search(vec, k, minScore);
                    fallback.add(one);
                } catch (Exception ex) {
                    logger.error("逐条 KNN 检索失败: {}", ex.getMessage());
                    fallback.add(java.util.Collections.emptyList());
                }
            }
            return fallback;
        }
    }

    /**
     * 批量 KNN 检索（_msearch）增加 phase 服务端过滤，每个向量可有不同 phase
     */
    public List<List<SearchResult>> multiKnnSearch(List<List<Float>> queryEmbeddings, int k, Double minScore, List<String> phases) {
        if (queryEmbeddings == null || queryEmbeddings.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        try {
            int numCandidates = Math.max(k * 2, k + 10);
            java.util.List<co.elastic.clients.elasticsearch.core.msearch.RequestItem> items = new java.util.ArrayList<>(queryEmbeddings.size());
            for (int i = 0; i < queryEmbeddings.size(); i++) {
                List<Float> vec = queryEmbeddings.get(i);
                String phase = (phases != null && i < phases.size()) ? phases.get(i) : null;
                co.elastic.clients.elasticsearch._types.query_dsl.Query phaseQuery = buildPhaseFilterQuery(phase);

                // 在 knn.filter 中加入 phase 过滤
                var knnBuilder = new co.elastic.clients.elasticsearch._types.KnnQuery.Builder()
                        .field("embedding")
                        .queryVector(vec)
                        .k(k)
                        .numCandidates(numCandidates);
                if (phaseQuery != null) {
                    knnBuilder.filter(phaseQuery);
                }

                co.elastic.clients.elasticsearch.core.msearch.RequestItem item =
                        co.elastic.clients.elasticsearch.core.msearch.RequestItem.of(ri -> ri
                                .header(h -> h.index(indexName))
                                .body(b -> b
                                        .size(k)
                                        .knn(knnBuilder.build())
                                        .minScore((minScore != null && minScore > 0.0) ? minScore : null)
                                )
                        );
                items.add(item);
            }

            MsearchRequest request = MsearchRequest.of(b -> b
                    .index(indexName)
                    .searches(items)
            );

            MsearchResponse<Map> response = elasticsearchClient.msearch(request, Map.class);

            List<List<SearchResult>> all = new ArrayList<>();
            for (MultiSearchResponseItem<Map> item : response.responses()) {
                List<SearchResult> results = new ArrayList<>();
                if (item != null && item.result() != null && item.result().hits() != null) {
                    for (Hit<Map> hit : item.result().hits().hits()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> source = hit.source();
                        if (source == null) continue;
                        String text = (String) source.get("text");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> metadata = (Map<String, Object>) source.get("metadata");
                        if (metadata == null) metadata = new java.util.HashMap<>();
                        metadata.put("score", hit.score());
                        metadata.put("id", hit.id());
                        results.add(new SearchResult(text, metadata));
                    }
                }
                all.add(results);
            }
            return all;
        } catch (Exception e) {
            logger.warn("_msearch (with phase) 失败，降级为逐条 KNN 检索: {}", e.getMessage());
            List<List<SearchResult>> fallback = new ArrayList<>(queryEmbeddings.size());
            for (int i = 0; i < queryEmbeddings.size(); i++) {
                try {
                    List<SearchResult> one = this.search(queryEmbeddings.get(i), k, minScore, phases != null && i < phases.size() ? phases.get(i) : null);
                    fallback.add(one);
                } catch (Exception ex) {
                    logger.error("逐条 KNN 检索失败: {}", ex.getMessage());
                    fallback.add(java.util.Collections.emptyList());
                }
            }
            return fallback;
        }
    }

    /**
     * 构建 phase 过滤查询：包含目标 phase 或缺失 phase（通用）
     */
    private co.elastic.clients.elasticsearch._types.query_dsl.Query buildPhaseFilterQuery(String phase) {
        if (phase == null) return null;
        String p = phase.trim();
        if (p.isEmpty()) return null;
        // 使用 match_phrase 兼容 metadata.phase 为 text 的动态映射
        return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q
                .bool(b -> b
                        .should(sh -> sh.matchPhrase(mp -> mp.field("metadata.phase").query(p)))
                        .should(sh -> sh.bool(bb -> bb.mustNot(mn -> mn.exists(e -> e.field("metadata.phase")))))
                        .minimumShouldMatch("1")
                )
        );
    }

    /**
     * 批量 BM25 检索（使用 _msearch）
     */
    public List<List<SearchResult>> bm25MultiSearch(List<String> queries, int topK, float boost) {
        if (queries == null || queries.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        try {
            StringBuilder ndjson = new StringBuilder();
            for (String q : queries) {
                ndjson.append("{\"index\":\"").append(indexName).append("\"}\n");
                ndjson.append("{");
                ndjson.append("\"size\":").append(topK).append(",");
                ndjson.append("\"query\":{\"bool\":{\"should\":[");
                // match on text
                ndjson.append("{\"match\":{\"text\":{\"query\":\"").append(escapeJson(q)).append("\",\"boost\":").append(boost).append("}}}");
                ndjson.append(",");
                // match on text.bm25
                ndjson.append("{\"match\":{\"text.bm25\":{\"query\":\"").append(escapeJson(q)).append("\",\"boost\":").append(boost).append("}}}");
                ndjson.append("]}}}");
                ndjson.append("}\n");
            }

            MsearchRequest request = new MsearchRequest.Builder()
                    .index(indexName)
                    .withJson(new java.io.StringReader(ndjson.toString()))
                    .build();

            MsearchResponse<Map> response = elasticsearchClient.msearch(request, Map.class);

            List<List<SearchResult>> all = new ArrayList<>();
            for (MultiSearchResponseItem<Map> item : response.responses()) {
                List<SearchResult> results = new ArrayList<>();
                if (item != null && item.result() != null && item.result().hits() != null) {
                    for (Hit<Map> hit : item.result().hits().hits()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> source = hit.source();
                        if (source == null) continue;
                        String text = (String) source.get("text");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> metadata = (Map<String, Object>) source.get("metadata");
                        if (metadata == null) metadata = new java.util.HashMap<>();
                        metadata.put("score", hit.score());
                        metadata.put("id", hit.id());
                        results.add(new SearchResult(text, metadata));
                    }
                }
                all.add(results);
            }
            return all;
        } catch (IOException e) {
            throw new RuntimeException("bm25MultiSearch failed: " + e.getMessage(), e);
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String toJsonArray(List<Float> vec) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < vec.size(); i++) {
            if (i > 0) sb.append(',');
            Float v = vec.get(i);
            sb.append(v != null ? v : 0.0f);
        }
        sb.append("]");
        return sb.toString();
    }



}
