#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
查看Elasticsearch索引结构
"""

import requests
import json

# Elasticsearch配置
ES_URL = "http://localhost:9200"
INDEX_NAME = "ai_loan1_ik"
USERNAME = "elastic"
PASSWORD = "yingzi"

def get_index_mapping():
    """获取索引映射结构"""
    url = f"{ES_URL}/{INDEX_NAME}/_mapping"
    
    try:
        response = requests.get(url, auth=(USERNAME, PASSWORD))
        
        if response.status_code == 200:
            data = response.json()
            print("=== 索引映射结构 ===")
            print(json.dumps(data, indent=2, ensure_ascii=False))
            return data
        else:
            print(f"获取映射失败: {response.status_code}")
            print(f"错误信息: {response.text}")
            return None
            
    except Exception as e:
        print(f"请求出错: {e}")
        return None

def get_index_settings():
    """获取索引设置"""
    url = f"{ES_URL}/{INDEX_NAME}/_settings"
    
    try:
        response = requests.get(url, auth=(USERNAME, PASSWORD))
        
        if response.status_code == 200:
            data = response.json()
            print("\n=== 索引设置 ===")
            print(json.dumps(data, indent=2, ensure_ascii=False))
            return data
        else:
            print(f"获取设置失败: {response.status_code}")
            print(f"错误信息: {response.text}")
            return None
            
    except Exception as e:
        print(f"请求出错: {e}")
        return None

def get_sample_documents():
    """获取示例文档"""
    url = f"{ES_URL}/{INDEX_NAME}/_search"
    
    query = {
        "query": {
            "match_all": {}
        },
        "size": 3
    }
    
    try:
        response = requests.post(url, json=query, auth=(USERNAME, PASSWORD))
        
        if response.status_code == 200:
            data = response.json()
            print("\n=== 示例文档 ===")
            
            hits = data.get("hits", {}).get("hits", [])
            for i, hit in enumerate(hits, 1):
                print(f"\n文档 {i}:")
                print(f"ID: {hit.get('_id')}")
                print(f"Source: {json.dumps(hit.get('_source', {}), indent=2, ensure_ascii=False)}")
                print("-" * 50)
                
            return data
        else:
            print(f"获取文档失败: {response.status_code}")
            print(f"错误信息: {response.text}")
            return None
            
    except Exception as e:
        print(f"请求出错: {e}")
        return None

def get_index_stats():
    """获取索引统计信息"""
    url = f"{ES_URL}/{INDEX_NAME}/_stats"
    
    try:
        response = requests.get(url, auth=(USERNAME, PASSWORD))
        
        if response.status_code == 200:
            data = response.json()
            print("\n=== 索引统计信息 ===")
            
            indices = data.get("indices", {})
            if INDEX_NAME in indices:
                stats = indices[INDEX_NAME]
                total = stats.get("total", {})
                docs = total.get("docs", {})
                store = total.get("store", {})
                
                print(f"文档数量: {docs.get('count', 0)}")
                print(f"已删除文档: {docs.get('deleted', 0)}")
                print(f"存储大小: {store.get('size_in_bytes', 0)} bytes")
                print(f"存储大小: {store.get('size', 'N/A')}")
                
            return data
        else:
            print(f"获取统计失败: {response.status_code}")
            print(f"错误信息: {response.text}")
            return None
            
    except Exception as e:
        print(f"请求出错: {e}")
        return None

if __name__ == "__main__":
    print(f"🔍 检查Elasticsearch索引结构: {INDEX_NAME}")
    print("=" * 60)
    
    # 获取索引统计信息
    get_index_stats()
    
    # 获取索引映射
    get_index_mapping()
    
    # 获取索引设置
    get_index_settings()
    
    # 获取示例文档
    get_sample_documents()