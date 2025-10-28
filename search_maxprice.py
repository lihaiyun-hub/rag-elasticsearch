#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
搜索包含maxPrice的文档
"""

import requests
import json

# Elasticsearch配置
ES_URL = "http://localhost:9200"
INDEX_NAME = "ai_loan1_ik"

def search_maxprice():
    """搜索包含maxPrice的文档"""
    
    url = f"{ES_URL}/{INDEX_NAME}/_search"
    
    # 使用通配符查询搜索包含maxPrice的文档
    query = {
        "query": {
            "wildcard": {
                "content": "*maxPrice*"
            }
        },
        "size": 50
    }
    
    try:
        response = requests.post(url, json=query, headers={"Content-Type": "application/json"})
        
        if response.status_code == 200:
            data = response.json()
            hits = data.get("hits", {}).get("hits", [])
            
            print(f"🔍 搜索包含 'maxPrice' 的文档，找到 {len(hits)} 个:")
            print("=" * 80)
            
            for i, hit in enumerate(hits):
                source = hit.get("_source", {})
                content = source.get("content", "")
                metadata = source.get("metadata", {})
                
                print(f"\n文档 {i+1}:")
                print(f"ID: {hit.get('_id')}")
                print(f"内容: {content}")
                print(f"元数据: {json.dumps(metadata, ensure_ascii=False, indent=2)}")
                print("-" * 40)
                
        else:
            print(f"搜索失败: {response.status_code}")
            print(f"错误信息: {response.text}")
            
    except Exception as e:
        print(f"搜索出错: {e}")

def search_template_variables():
    """搜索包含模板变量的文档"""
    
    url = f"{ES_URL}/{INDEX_NAME}/_search"
    
    # 搜索包含 ${ 的文档
    query = {
        "query": {
            "wildcard": {
                "content": "*${*"
            }
        },
        "size": 50
    }
    
    try:
        response = requests.post(url, json=query, headers={"Content-Type": "application/json"})
        
        if response.status_code == 200:
            data = response.json()
            hits = data.get("hits", {}).get("hits", [])
            
            print(f"\n🔍 搜索包含模板变量的文档，找到 {len(hits)} 个:")
            print("=" * 80)
            
            for i, hit in enumerate(hits):
                source = hit.get("_source", {})
                content = source.get("content", "")
                metadata = source.get("metadata", {})
                
                print(f"\n文档 {i+1}:")
                print(f"ID: {hit.get('_id')}")
                print(f"内容: {content}")
                print(f"元数据: {json.dumps(metadata, ensure_ascii=False, indent=2)}")
                print("-" * 40)
                
        else:
            print(f"搜索失败: {response.status_code}")
            print(f"错误信息: {response.text}")
            
    except Exception as e:
        print(f"搜索出错: {e}")

if __name__ == "__main__":
    print("🔍 搜索包含maxPrice和模板变量的文档")
    
    # 搜索maxPrice
    search_maxprice()
    
    # 搜索模板变量
    search_template_variables()