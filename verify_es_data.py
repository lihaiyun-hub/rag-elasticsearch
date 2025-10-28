#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from elasticsearch import Elasticsearch
import json

def verify_es_data():
    """验证ES中的数据存储和检索功能"""
    
    # 连接ES
    es = Elasticsearch(['http://localhost:9200'])
    index_name = 'customer_support_qa'
    
    print("=== 索引状态 ===")
    try:
        index_stats = es.indices.stats(index=index_name)
        doc_count = index_stats['indices'][index_name]['total']['docs']['count']
        print(f"索引名称: {index_name}")
        print(f"文档数量: {doc_count}")
        size_bytes = index_stats['indices'][index_name]['total']['store']['size_in_bytes']
        print(f"索引大小: {size_bytes} bytes")
    except Exception as e:
        print(f"获取索引状态失败: {e}")
        return
    
    print("\n=== 索引映射 ===")
    try:
        mapping = es.indices.get_mapping(index=index_name)
        properties = mapping[index_name]['mappings']['properties']
        for field, config in properties.items():
            field_type = config.get('type', 'object')
            print(f"{field}: {field_type}")
    except Exception as e:
        print(f"获取索引映射失败: {e}")
    
    print("\n=== 示例文档 ===")
    try:
        search_result = es.search(
            index=index_name,
            body={
                'query': {'match_all': {}},
                'size': 3,
                '_source': ['latest_question', 'operation', 'parameters', 'intent']
            }
        )
        
        for i, hit in enumerate(search_result['hits']['hits'], 1):
            source = hit['_source']
            print(f"{i}. 问题: {source.get('latest_question', '')}")
            print(f"   操作: {source.get('operation', '')}")
            print(f"   参数: {json.dumps(source.get('parameters', {}), ensure_ascii=False)}")
            print(f"   意图: {source.get('intent', '')}")
            print()
    except Exception as e:
        print(f"查询示例文档失败: {e}")
    
    print("=== 向量搜索测试 ===")
    try:
        from sentence_transformers import SentenceTransformer
        model = SentenceTransformer('all-MiniLM-L6-v2')
        
        query = "我想借10000元"
        query_embedding = model.encode(query).tolist()
        
        search_body = {
            'query': {
                'script_score': {
                    'query': {'match_all': {}},
                    'script': {
                        'source': 'cosineSimilarity(params.query_vector, "question_embedding") + 1.0',
                        'params': {'query_vector': query_embedding}
                    }
                }
            },
            'size': 3,
            '_source': ['latest_question', 'operation', 'parameters']
        }
        
        response = es.search(index=index_name, body=search_body)
        
        print(f"查询: {query}")
        for i, hit in enumerate(response['hits']['hits'], 1):
            source = hit['_source']
            score = hit['_score']
            print(f"{i}. {source['latest_question']} (相似度: {score:.3f})")
            print(f"   操作: {source['operation']}, 参数: {json.dumps(source['parameters'], ensure_ascii=False)}")
            print()
            
    except Exception as e:
        print(f"向量搜索失败: {e}")

if __name__ == "__main__":
    verify_es_data()