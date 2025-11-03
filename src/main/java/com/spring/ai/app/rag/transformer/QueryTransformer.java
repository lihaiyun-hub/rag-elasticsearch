package com.spring.ai.app.rag.transformer;

import com.spring.ai.app.rag.model.Query;

/**
 * 查询转换器接口
 */
public interface QueryTransformer {
    /**
     * 转换查询
     *
     * @param query 原始查询
     * @return 转换后的查询
     */
    Query transform(Query query);
}