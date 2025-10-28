#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
检查多个Elasticsearch索引的数据情况
"""

import requests
import json
from datetime import datetime

# Elasticsearch配置
ES_URL = "http://localhost:9200"
USERNAME = "elastic"
PASSWORD = "yingzi"

# 要检查的索引
INDICES = ["ai_loan1_ik", "customer_support_qa"]

def check_index_exists(index_name):
    """检查索引是否存在"""
    url = f"{ES_URL}/{index_name}"
    
    try:
        response = requests.head(url, auth=(USERNAME, PASSWORD))
        return response.status_code == 200
    except Exception as e:
        print(f"检查索引 {index_name} 存在性时出错: {e}")
        return False

def get_index_stats(index_name):
    """获取索引统计信息"""
    url = f"{ES_URL}/{index_name}/_stats"
    
    try:
        response = requests.get(url, auth=(USERNAME, PASSWORD))
        
        if response.status_code == 200:
            data = response.json()
            indices = data.get('indices', {})
            if index_name in indices:
                index_stats = indices[index_name]
                total = index_stats.get('total', {})
                
                stats = {
                    'doc_count': total.get('docs', {}).get('count', 0),
                    'deleted_docs': total.get('docs', {}).get('deleted', 0),
                    'store_size_mb': total.get('store', {}).get('size_in_bytes', 0) / 1024 / 1024,
                    'segments': total.get('segments', {}).get('count', 0)
                }
                return stats
        return None
            
    except Exception as e:
        print(f"获取索引 {index_name} 统计信息时出错: {e}")
        return None

def get_recent_documents(index_name, size=5):
    """获取最近的文档"""
    url = f"{ES_URL}/{index_name}/_search"
    
    query = {
        "size": size,
        "sort": [
            {"_id": {"order": "desc"}}
        ],
        "query": {
            "match_all": {}
        }
    }
    
    try:
        response = requests.post(url, 
                               json=query, 
                               auth=(USERNAME, PASSWORD),
                               headers={'Content-Type': 'application/json'})
        
        if response.status_code == 200:
            data = response.json()
            hits = data.get('hits', {}).get('hits', [])
            return hits
        else:
            print(f"获取索引 {index_name} 文档失败: {response.status_code}")
            return []
            
    except Exception as e:
        print(f"获取索引 {index_name} 文档时出错: {e}")
        return []

def search_excel_source_data(index_name):
    """搜索来源为Excel的数据"""
    url = f"{ES_URL}/{index_name}/_search"
    
    query = {
        "size": 10,
        "query": {
            "bool": {
                "should": [
                    {"term": {"source": "excel_import"}},
                    {"term": {"metadata.source": "excel_import"}},
                    {"wildcard": {"content": "*思考过程*"}},
                    {"wildcard": {"content": "*历史提问*"}},
                    {"exists": {"field": "historical_question"}},
                    {"exists": {"field": "latest_question"}}
                ]
            }
        }
    }
    
    try:
        response = requests.post(url, 
                               json=query, 
                               auth=(USERNAME, PASSWORD),
                               headers={'Content-Type': 'application/json'})
        
        if response.status_code == 200:
            data = response.json()
            hits = data.get('hits', {}).get('hits', [])
            return hits
        else:
            print(f"搜索索引 {index_name} Excel数据失败: {response.status_code}")
            return []
            
    except Exception as e:
        print(f"搜索索引 {index_name} Excel数据时出错: {e}")
        return []

def get_index_mapping(index_name):
    """获取索引映射结构"""
    url = f"{ES_URL}/{index_name}/_mapping"
    
    try:
        response = requests.get(url, auth=(USERNAME, PASSWORD))
        
        if response.status_code == 200:
            data = response.json()
            if index_name in data:
                mappings = data[index_name].get('mappings', {})
                properties = mappings.get('properties', {})
                return list(properties.keys())
        return []
            
    except Exception as e:
        print(f"获取索引 {index_name} 映射时出错: {e}")
        return []

def main():
    print("🔍 检查多个Elasticsearch索引的数据情况")
    print("=" * 80)
    
    for index_name in INDICES:
        print(f"\n📊 索引: {index_name}")
        print("-" * 50)
        
        # 检查索引是否存在
        if not check_index_exists(index_name):
            print(f"❌ 索引 {index_name} 不存在")
            continue
        
        print(f"✅ 索引 {index_name} 存在")
        
        # 获取统计信息
        stats = get_index_stats(index_name)
        if stats:
            print(f"📈 统计信息:")
            print(f"   文档数量: {stats['doc_count']}")
            print(f"   存储大小: {stats['store_size_mb']:.2f} MB")
            print(f"   段数量: {stats['segments']}")
        
        # 获取字段列表
        fields = get_index_mapping(index_name)
        if fields:
            print(f"🏷️  字段列表: {', '.join(fields)}")
        
        # 搜索Excel来源的数据
        excel_docs = search_excel_source_data(index_name)
        if excel_docs:
            print(f"📄 Excel来源数据: 找到 {len(excel_docs)} 条")
            for i, doc in enumerate(excel_docs[:3], 1):
                source = doc['_source']
                print(f"   {i}. ID: {doc['_id']}")
                
                # 显示关键字段
                if 'latest_question' in source:
                    print(f"      最新提问: {source['latest_question'][:50]}...")
                elif 'content' in source:
                    print(f"      内容: {source['content'][:50]}...")
                
                if 'source' in source:
                    print(f"      来源: {source['source']}")
                elif 'metadata' in source and isinstance(source['metadata'], dict):
                    metadata_source = source['metadata'].get('source', '')
                    if metadata_source:
                        print(f"      来源: {metadata_source}")
        else:
            print(f"📄 Excel来源数据: 未找到")
        
        # 获取最近的文档示例
        recent_docs = get_recent_documents(index_name, 2)
        if recent_docs:
            print(f"🕒 最近文档示例:")
            for i, doc in enumerate(recent_docs, 1):
                source = doc['_source']
                print(f"   {i}. ID: {doc['_id']}")
                
                # 显示内容摘要
                if 'content' in source:
                    content = source['content']
                    if len(content) > 100:
                        print(f"      内容: {content[:100]}...")
                    else:
                        print(f"      内容: {content}")
                
                # 显示元数据
                if 'metadata' in source and isinstance(source['metadata'], dict):
                    metadata = source['metadata']
                    if 'question_text' in metadata:
                        print(f"      问题: {metadata['question_text']}")
                    if 'type' in metadata:
                        print(f"      类型: {metadata['type']}")

if __name__ == "__main__":
    main()