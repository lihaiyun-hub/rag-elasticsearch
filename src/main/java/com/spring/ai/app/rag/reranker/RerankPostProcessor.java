package com.spring.ai.app.rag.reranker;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Order(100)
@Component
public class RerankPostProcessor implements DocumentPostProcessor {

    private final RerankService rerankService;

    public RerankPostProcessor(RerankService rerankService) {
        this.rerankService = rerankService;
    }

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        if (documents.isEmpty() || !rerankService.isEnabled()) {
            return documents;
        }
        List<String> texts = documents.stream().map(Document::getText).collect(Collectors.toList());
        List<RerankService.ResultItem> result = rerankService.rerank(query.text(), texts);
        if (result.isEmpty()) return documents;

        List<Document> reordered = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (RerankService.ResultItem item : result) {
            int idx = item.index;
            if (idx >= 0 && idx < documents.size() && used.add(idx)) {
                reordered.add(documents.get(idx));
            }
        }
        for (int i = 0; i < documents.size(); i++) {
            if (!used.contains(i)) reordered.add(documents.get(i));
        }
        return reordered;
    }
}