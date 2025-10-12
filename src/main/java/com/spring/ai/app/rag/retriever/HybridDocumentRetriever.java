package com.spring.ai.app.rag.retriever;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Component
public class HybridDocumentRetriever implements DocumentRetriever {

    private final DocumentRetriever bm25DocumentRetriever;
    private final DocumentRetriever vectorStoreDocumentRetriever;

    // 最终输出的 Top-K
    @Value("${spring.ai.rag.hybrid.top-k}")
    private int hybridTopK;

    // RRF 融合的平滑参数 k（越大越平滑）
    @Value("${spring.ai.rag.hybrid.rrf-k}")
    private int rrfK;

    public HybridDocumentRetriever(
            @Qualifier("BM25DocumentRetriever") DocumentRetriever bm25DocumentRetriever,
            @Qualifier("vectorStoreDocumentRetriever") DocumentRetriever vectorStoreDocumentRetriever) {
        this.bm25DocumentRetriever = bm25DocumentRetriever;
        this.vectorStoreDocumentRetriever = vectorStoreDocumentRetriever;
    }

    @Override
    public List<Document> retrieve(Query query) {
        // 双通道召回（候选大小由各自 retriever 内部配置控制）
        List<Document> bm25Docs = safeList(this.bm25DocumentRetriever.retrieve(query));
        List<Document> vecDocs = safeList(this.vectorStoreDocumentRetriever.retrieve(query));

        // 使用 RRF（Reciprocal Rank Fusion）融合，按排名位置计算分数
        Map<String, FusionEntry> fused = new LinkedHashMap<>();
        applyRrf(fused, bm25Docs, "bm25");
        applyRrf(fused, vecDocs, "vec");

        // 排序：按融合分数从高到低
        List<FusionEntry> sorted = new ArrayList<>(fused.values());
        sorted.sort((a, b) -> Double.compare(b.score, a.score));

        // 截断到 Top-K
        List<Document> result = new ArrayList<>();
        for (int i = 0; i < Math.min(hybridTopK, sorted.size()); i++) {
            result.add(sorted.get(i).doc);
        }
        return result;
    }

    private void applyRrf(Map<String, FusionEntry> fused, List<Document> docs, String channel) {
        for (int i = 0; i < docs.size(); i++) {
            Document d = docs.get(i);
            String key = uniqueKeyFor(d);
            double rrfScore = 1.0 / (rrfK + (i + 1)); // rank 从 1 开始
            fused.compute(key, (k, entry) -> {
                if (entry == null) {
                    entry = new FusionEntry(d, 0.0);
                }
                entry.score += rrfScore;
                return entry;
            });
        }
    }

    private static List<Document> safeList(List<Document> list) {
        return (list == null) ? Collections.emptyList() : list;
    }

    // 生成稳定的去重键：优先 id，其次常见 metadata 键，最后用内容哈希兜底
    private static String uniqueKeyFor(Document d) {
        if (d == null) return "";
        if (d.getId() != null && !d.getId().isEmpty()) return d.getId();
        Map<String, Object> meta = d.getMetadata();
        if (meta != null) {
            for (String k : new String[]{"id", "uri", "source", "canonical_id"}) {
                Object v = meta.get(k);
                if (v != null) {
                    String s = String.valueOf(v);
                    if (!s.isEmpty()) return s;
                }
            }
        }
        // 兜底：对内容做哈希
       return sha1(d.getText());
     }
 
     private static String sha1(String input) {
         try {
             MessageDigest md = MessageDigest.getInstance("SHA-1");
             byte[] bytes = md.digest(Objects.toString(input, "").getBytes(StandardCharsets.UTF_8));
             StringBuilder sb = new StringBuilder();
             for (byte b : bytes) sb.append(String.format("%02x", b));
             return sb.toString();
         } catch (NoSuchAlgorithmException e) {
             return Integer.toHexString(Objects.toString(input, "").hashCode());
         }
     }

    private static class FusionEntry {
        final Document doc;
        double score;
        FusionEntry(Document doc, double score) { this.doc = doc; this.score = score; }
    }
}