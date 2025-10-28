#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
调试检索问题 - 分析为什么"我要借钱"没有检索到"我要借款"
"""

import requests
import json

# Elasticsearch配置
ES_URL = "http://localhost:9200"
INDEX_NAME = "customer_support_qa"
USERNAME = "elastic"
PASSWORD = "yingzi"

def check_data_in_index():
    """检查索引中的数据"""
    
    print("=" * 60)
    print("1. 检查索引中的数据")
    print("=" * 60)
    
    # 查询所有思考过程数据
    search_url = f"{ES_URL}/{INDEX_NAME}/_search"
    query = {
        "query": {
            "bool": {
                "must": [
                    {"term": {"metadata.source.keyword": "思考过程补充结果"}}
                ]
            }
        },
        "size": 50,
        "_source": ["content", "metadata.latest_question", "metadata.intent"]
    }
    
    try:
        response = requests.post(search_url, json=query, auth=(USERNAME, PASSWORD))
        
        if response.status_code == 200:
            data = response.json()
            hits = data.get("hits", {}).get("hits", [])
            total = data.get("hits", {}).get("total", {}).get("value", 0)
            
            print(f"✅ 找到 {total} 个思考过程数据文档")
            
            # 查找包含"借款"或"借钱"的记录
            loan_records = []
            for hit in hits:
                source = hit.get("_source", {})
                content = source.get("content", "")
                question = source.get("metadata", {}).get("latest_question", "")
                
                if "借" in content or "借" in question:
                    loan_records.append({
                        "question": question,
                        "content": content[:200] + "..." if len(content) > 200 else content,
                        "doc_id": hit.get("_id")
                    })
            
            print(f"\n📋 找到 {len(loan_records)} 个包含'借'字的记录:")
            for i, record in enumerate(loan_records[:10]):  # 只显示前10个
                print(f"\n记录 {i+1}:")
                print(f"  问题: {record['question']}")
                print(f"  内容: {record['content']}")
                print(f"  文档ID: {record['doc_id']}")
                
        else:
            print(f"❌ 查询失败: {response.status_code}")
            print(f"错误信息: {response.text}")
            
    except Exception as e:
        print(f"❌ 检查数据时出错: {e}")

def test_exact_match():
    """测试精确匹配"""
    
    print("\n" + "=" * 60)
    print("2. 测试精确匹配")
    print("=" * 60)
    
    test_queries = ["我要借款", "我要借钱", "借款", "借钱"]
    
    for query_text in test_queries:
        print(f"\n🔍 精确搜索: '{query_text}'")
        print("-" * 30)
        
        search_url = f"{ES_URL}/{INDEX_NAME}/_search"
        query = {
            "query": {
                "bool": {
                    "must": [
                        {"term": {"metadata.source.keyword": "思考过程补充结果"}},
                        {"match": {"content": query_text}}
                    ]
                }
            },
            "size": 5,
            "_source": ["metadata.latest_question", "content"]
        }
        
        try:
            response = requests.post(search_url, json=query, auth=(USERNAME, PASSWORD))
            
            if response.status_code == 200:
                data = response.json()
                hits = data.get("hits", {}).get("hits", [])
                total = data.get("hits", {}).get("total", {}).get("value", 0)
                
                print(f"找到 {total} 个匹配文档")
                
                for i, hit in enumerate(hits):
                    source = hit.get("_source", {})
                    question = source.get("metadata", {}).get("latest_question", "")
                    content = source.get("content", "")[:100]
                    score = hit.get("_score", 0)
                    
                    print(f"  {i+1}. 问题: {question} (得分: {score:.2f})")
                    print(f"     内容: {content}...")
                    
            else:
                print(f"搜索失败: {response.status_code}")
                
        except Exception as e:
            print(f"搜索出错: {e}")

def test_vector_search():
    """测试向量搜索"""
    
    print("\n" + "=" * 60)
    print("3. 测试向量搜索")
    print("=" * 60)
    
    # 检查是否有向量字段
    mapping_url = f"{ES_URL}/{INDEX_NAME}/_mapping"
    
    try:
        response = requests.get(mapping_url, auth=(USERNAME, PASSWORD))
        
        if response.status_code == 200:
            mapping = response.json()
            properties = mapping.get(INDEX_NAME, {}).get("mappings", {}).get("properties", {})
            
            print("📋 索引字段映射:")
            for field_name, field_config in properties.items():
                field_type = field_config.get("type", "unknown")
                print(f"  {field_name}: {field_type}")
                
                # 检查是否有向量字段
                if field_type == "dense_vector":
                    print(f"    ✅ 发现向量字段: {field_name}")
                    dims = field_config.get("dims", "unknown")
                    print(f"    维度: {dims}")
                    
        else:
            print(f"❌ 获取映射失败: {response.status_code}")
            
    except Exception as e:
        print(f"❌ 检查映射时出错: {e}")

def test_fuzzy_search():
    """测试模糊搜索"""
    
    print("\n" + "=" * 60)
    print("4. 测试模糊搜索")
    print("=" * 60)
    
    query_text = "我要借钱"
    
    print(f"🔍 模糊搜索: '{query_text}'")
    print("-" * 30)
    
    search_url = f"{ES_URL}/{INDEX_NAME}/_search"
    query = {
        "query": {
            "bool": {
                "must": [
                    {"term": {"metadata.source.keyword": "思考过程补充结果"}}
                ],
                "should": [
                    {"fuzzy": {"content": {"value": query_text, "fuzziness": "AUTO"}}},
                    {"fuzzy": {"metadata.latest_question": {"value": query_text, "fuzziness": "AUTO"}}},
                    {"match": {"content": {"query": query_text, "fuzziness": "AUTO"}}},
                    {"match": {"metadata.latest_question": {"query": query_text, "fuzziness": "AUTO"}}}
                ],
                "minimum_should_match": 1
            }
        },
        "size": 10,
        "_source": ["metadata.latest_question", "content"]
    }
    
    try:
        response = requests.post(search_url, json=query, auth=(USERNAME, PASSWORD))
        
        if response.status_code == 200:
            data = response.json()
            hits = data.get("hits", {}).get("hits", [])
            total = data.get("hits", {}).get("total", {}).get("value", 0)
            
            print(f"找到 {total} 个模糊匹配文档")
            
            for i, hit in enumerate(hits):
                source = hit.get("_source", {})
                question = source.get("metadata", {}).get("latest_question", "")
                content = source.get("content", "")[:100]
                score = hit.get("_score", 0)
                
                print(f"  {i+1}. 问题: {question} (得分: {score:.2f})")
                print(f"     内容: {content}...")
                
        else:
            print(f"模糊搜索失败: {response.status_code}")
            
    except Exception as e:
        print(f"模糊搜索出错: {e}")

def check_analyzer():
    """检查分析器配置"""
    
    print("\n" + "=" * 60)
    print("5. 检查分析器配置")
    print("=" * 60)
    
    # 测试分析器如何处理查询文本
    analyze_url = f"{ES_URL}/{INDEX_NAME}/_analyze"
    
    test_texts = ["我要借钱", "我要借款", "借钱", "借款"]
    
    for text in test_texts:
        print(f"\n🔍 分析文本: '{text}'")
        print("-" * 20)
        
        analyze_query = {
            "analyzer": "standard",
            "text": text
        }
        
        try:
            response = requests.post(analyze_url, json=analyze_query, auth=(USERNAME, PASSWORD))
            
            if response.status_code == 200:
                data = response.json()
                tokens = data.get("tokens", [])
                
                print("分词结果:")
                for token in tokens:
                    print(f"  - {token.get('token')} (位置: {token.get('position')})")
                    
            else:
                print(f"分析失败: {response.status_code}")
                
        except Exception as e:
            print(f"分析出错: {e}")

if __name__ == "__main__":
    check_data_in_index()
    test_exact_match()
    test_vector_search()
    test_fuzzy_search()
    check_analyzer()
    
    print("\n" + "=" * 60)
    print("🔍 调试完成！请查看上述结果分析检索问题")
    print("=" * 60)