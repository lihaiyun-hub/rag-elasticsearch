#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
查看Elasticsearch索引的详细映射结构
"""

import requests
import json

# Elasticsearch配置
ES_URL = "http://localhost:9200"
INDEX_NAME = "ai_loan1_ik"
USERNAME = "elastic"
PASSWORD = "yingzi"

def check_index_exists():
    """检查索引是否存在"""
    url = f"{ES_URL}/{INDEX_NAME}"
    
    try:
        response = requests.head(url, auth=(USERNAME, PASSWORD))
        return response.status_code == 200
    except Exception as e:
        print(f"检查索引存在性时出错: {e}")
        return False

def get_index_mapping():
    """获取索引映射结构"""
    url = f"{ES_URL}/{INDEX_NAME}/_mapping"
    
    try:
        response = requests.get(url, auth=(USERNAME, PASSWORD))
        
        if response.status_code == 200:
            data = response.json()
            print("=== 索引映射结构 ===")
            print(json.dumps(data, indent=2, ensure_ascii=False))
            
            # 提取字段信息
            if INDEX_NAME in data:
                mappings = data[INDEX_NAME].get('mappings', {})
                properties = mappings.get('properties', {})
                
                print("\n=== 字段详细信息 ===")
                for field_name, field_config in properties.items():
                    print(f"字段名: {field_name}")
                    print(f"  类型: {field_config.get('type', 'unknown')}")
                    if 'dims' in field_config:
                        print(f"  维度: {field_config['dims']}")
                    if 'similarity' in field_config:
                        print(f"  相似度函数: {field_config['similarity']}")
                    if 'analyzer' in field_config:
                        print(f"  分析器: {field_config['analyzer']}")
                    print()
            
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
            print("=== 索引设置 ===")
            print(json.dumps(data, indent=2, ensure_ascii=False))
            return data
        else:
            print(f"获取设置失败: {response.status_code}")
            return None
            
    except Exception as e:
        print(f"请求出错: {e}")
        return None

def get_sample_documents(size=3):
    """获取示例文档"""
    url = f"{ES_URL}/{INDEX_NAME}/_search"
    
    query = {
        "size": size,
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
            print(f"=== 示例文档 (前{size}条) ===")
            
            hits = data.get('hits', {}).get('hits', [])
            for i, hit in enumerate(hits, 1):
                print(f"\n--- 文档 {i} ---")
                print(f"ID: {hit['_id']}")
                source = hit['_source']
                
                # 显示文档结构
                for key, value in source.items():
                    if key == 'embedding':
                        print(f"{key}: [向量数据，长度: {len(value) if isinstance(value, list) else 'unknown'}]")
                    elif isinstance(value, str) and len(value) > 100:
                        print(f"{key}: {value[:100]}...")
                    else:
                        print(f"{key}: {value}")
            
            print(f"\n总文档数: {data.get('hits', {}).get('total', {}).get('value', 0)}")
            return data
        else:
            print(f"获取文档失败: {response.status_code}")
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
            print("=== 索引统计信息 ===")
            
            indices = data.get('indices', {})
            if INDEX_NAME in indices:
                index_stats = indices[INDEX_NAME]
                total = index_stats.get('total', {})
                
                print(f"文档数量: {total.get('docs', {}).get('count', 0)}")
                print(f"已删除文档: {total.get('docs', {}).get('deleted', 0)}")
                
                store_size = total.get('store', {}).get('size_in_bytes', 0)
                print(f"存储大小: {store_size / 1024 / 1024:.2f} MB")
                
                segments = total.get('segments', {})
                print(f"段数量: {segments.get('count', 0)}")
                
            return data
        else:
            print(f"获取统计信息失败: {response.status_code}")
            return None
            
    except Exception as e:
        print(f"请求出错: {e}")
        return None

if __name__ == "__main__":
    print(f"🔍 检查Elasticsearch索引结构: {INDEX_NAME}")
    print("=" * 60)
    
    # 检查索引是否存在
    if not check_index_exists():
        print(f"❌ 索引 {INDEX_NAME} 不存在")
        exit(1)
    
    print(f"✅ 索引 {INDEX_NAME} 存在")
    print()
    
    # 获取索引统计信息
    get_index_stats()
    print()
    
    # 获取索引映射
    get_index_mapping()
    print()
    
    # 获取索引设置
    get_index_settings()
    print()
    
    # 获取示例文档
    get_sample_documents()