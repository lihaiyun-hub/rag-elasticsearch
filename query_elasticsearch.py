#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
查询Elasticsearch中的知识库数据
"""

import requests
import json

# Elasticsearch配置
ES_URL = "http://localhost:9200"
INDEX_NAME = "ai_loan1_ik"

def query_all_documents():
    """查询所有文档"""
    
    url = f"{ES_URL}/{INDEX_NAME}/_search"
    
    query = {
        "query": {
            "match_all": {}
        },
        "size": 100
    }
    
    try:
        response = requests.post(url, json=query, headers={"Content-Type": "application/json"})
        
        if response.status_code == 200:
            data = response.json()
            hits = data.get("hits", {}).get("hits", [])
            
            print(f"找到 {len(hits)} 个文档:")
            print("=" * 80)
            
            for i, hit in enumerate(hits):
                source = hit.get("_source", {})
                content = source.get("content", "")
                metadata = source.get("metadata", {})
                
                print(f"\n文档 {i+1}:")
                print(f"ID: {hit.get('_id')}")
                print(f"内容: {content}")
                print(f"元数据: {json.dumps(metadata, ensure_ascii=False, indent=2)}")
                
                # 检查是否包含模板变量
                if "${" in content and "}" in content:
                    print(f"⚠️  发现模板变量: {content}")
                
                print("-" * 40)
                
        else:
            print(f"查询失败: {response.status_code}")
            print(f"错误信息: {response.text}")
            
    except Exception as e:
        print(f"查询出错: {e}")

def search_by_content(search_text):
    """根据内容搜索文档"""
    
    url = f"{ES_URL}/{INDEX_NAME}/_search"
    
    query = {
        "query": {
            "match": {
                "content": search_text
            }
        }
    }
    
    try:
        response = requests.post(url, json=query, headers={"Content-Type": "application/json"})
        
        if response.status_code == 200:
            data = response.json()
            hits = data.get("hits", {}).get("hits", [])
            
            print(f"\n搜索 '{search_text}' 找到 {len(hits)} 个文档:")
            
            for hit in hits:
                source = hit.get("_source", {})
                content = source.get("content", "")
                score = hit.get("_score", 0)
                
                print(f"评分: {score}")
                print(f"内容: {content}")
                print("-" * 40)
                
        else:
            print(f"搜索失败: {response.status_code}")
            
    except Exception as e:
        print(f"搜索出错: {e}")

if __name__ == "__main__":
    print("🔍 查询Elasticsearch知识库数据")
    
    # 查询所有文档
    query_all_documents()
    
    # 搜索包含"额度"的文档
    search_by_content("额度")