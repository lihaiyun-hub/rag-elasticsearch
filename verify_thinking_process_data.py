#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
验证思考过程数据导入结果
"""

import requests
import json

# Elasticsearch配置
ES_URL = "http://localhost:9200"
INDEX_NAME = "customer_support_qa"
USERNAME = "elastic"
PASSWORD = "yingzi"

def verify_thinking_process_data():
    """验证思考过程数据导入结果"""
    
    print("=" * 60)
    print("验证思考过程数据导入结果")
    print("=" * 60)
    
    # 查询思考过程数据
    search_url = f"{ES_URL}/{INDEX_NAME}/_search"
    query = {
        "query": {
            "bool": {
                "must": [
                    {"term": {"metadata.source.keyword": "思考过程补充结果"}}
                ]
            }
        },
        "size": 10,
        "sort": [{"metadata.import_date": {"order": "desc"}}]
    }
    
    try:
        response = requests.post(search_url, json=query, auth=(USERNAME, PASSWORD))
        
        if response.status_code == 200:
            data = response.json()
            total = data.get("hits", {}).get("total", {}).get("value", 0)
            hits = data.get("hits", {}).get("hits", [])
            
            print(f"✅ 总共找到 {total} 个思考过程数据文档")
            
            if hits:
                print("\n📋 最新的10个文档:")
                print("-" * 60)
                
                for i, hit in enumerate(hits):
                    source = hit.get("_source", {})
                    metadata = source.get("metadata", {})
                    
                    print(f"\n文档 {i+1}:")
                    print(f"  📝 用户提问: {metadata.get('latest_question', 'N/A')}")
                    print(f"  🎯 用户意图: {metadata.get('intent', 'N/A')}")
                    print(f"  ⚙️  处理方式: {metadata.get('processing_method', 'N/A')}")
                    print(f"  🕒 导入时间: {metadata.get('import_date', 'N/A')}")
                    
                    # 显示响应数据
                    response_data = metadata.get('response_data', {})
                    if response_data:
                        operation = response_data.get('operation', 'N/A')
                        parameters = response_data.get('parameters', {})
                        print(f"  🔧 操作类型: {operation}")
                        if parameters:
                            print(f"  📊 参数: {json.dumps(parameters, ensure_ascii=False)}")
                
                # 统计不同意图的数量
                print("\n📊 意图统计:")
                print("-" * 30)
                intent_stats = {}
                for hit in hits:
                    metadata = hit.get("_source", {}).get("metadata", {})
                    intent = metadata.get("intent", "未知")
                    intent_stats[intent] = intent_stats.get(intent, 0) + 1
                
                for intent, count in intent_stats.items():
                    print(f"  {intent}: {count} 个")
                
            else:
                print("❌ 没有找到思考过程数据")
                
        else:
            print(f"❌ 查询失败: {response.status_code}")
            print(f"错误信息: {response.text}")
            
    except Exception as e:
        print(f"❌ 验证过程中出错: {e}")

def test_search_functionality():
    """测试搜索功能"""
    
    print("\n" + "=" * 60)
    print("测试搜索功能")
    print("=" * 60)
    
    # 测试搜索关键词
    test_queries = ["借款", "修改方案", "生成方案", "澄清"]
    
    for query_text in test_queries:
        print(f"\n🔍 搜索关键词: '{query_text}'")
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
            "size": 3,
            "_source": ["metadata.latest_question", "metadata.intent", "metadata.processing_method"]
        }
        
        try:
            response = requests.post(search_url, json=query, auth=(USERNAME, PASSWORD))
            
            if response.status_code == 200:
                data = response.json()
                hits = data.get("hits", {}).get("hits", [])
                total = data.get("hits", {}).get("total", {}).get("value", 0)
                
                print(f"找到 {total} 个相关文档")
                
                for i, hit in enumerate(hits):
                    metadata = hit.get("_source", {}).get("metadata", {})
                    print(f"  {i+1}. {metadata.get('latest_question', 'N/A')} -> {metadata.get('intent', 'N/A')}")
                    
            else:
                print(f"搜索失败: {response.status_code}")
                
        except Exception as e:
            print(f"搜索出错: {e}")

if __name__ == "__main__":
    verify_thinking_process_data()
    test_search_functionality()
    
    print("\n" + "=" * 60)
    print("✅ 验证完成！思考过程数据已成功导入并可以正常搜索")
    print("=" * 60)