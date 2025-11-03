package com.spring.ai.app.rag.retriever;

import com.spring.ai.app.rag.model.Document;
import com.spring.ai.app.rag.model.Query;

import java.util.List;

/**
 * 文档检索器接口
 */
public interface DocumentRetriever {
    /**
     * 根据查询检索相关文档
     * @param query 查询对象
     * @return 相关文档列表
     */
    List<Document> retrieve(Query query);

    /**
     * 批量检索：默认逐条调用 {@link #retrieve(Query)}，具体实现可覆盖以进行批量优化（如 msearch）。
     * @param queries 查询对象列表
     * @return 每个查询对应的文档列表集合，顺序与输入保持一致
     */
     default List<List<Document>> retrieveBatch(List<Query> queries) {
         if (queries == null || queries.isEmpty()) {
             return java.util.Collections.emptyList();
         }
         java.util.List<java.util.List<Document>> results = new java.util.ArrayList<>(queries.size());
         for (Query q : queries) {
             results.add(retrieve(q));
         }
         return results;
     }
}
