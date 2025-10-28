#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
修复向量存储问题 - 重新导入数据并生成向量嵌入
"""

import requests
import json
import pandas as pd
from datetime import datetime
import time

# Elasticsearch配置
ES_URL = "http://localhost:9200"
USERNAME = "elastic"
PASSWORD = "yingzi"
INDEX_NAME = "customer_support_qa"

def check_index_mapping():
    """检查当前索引映射"""
    
    print("=" * 60)
    print("1. 检查当前索引映射")
    print("=" * 60)
    
    mapping_url = f"{ES_URL}/{INDEX_NAME}/_mapping"
    
    try:
        response = requests.get(mapping_url, auth=(USERNAME, PASSWORD))
        
        if response.status_code == 200:
            mapping = response.json()
            properties = mapping.get(INDEX_NAME, {}).get("mappings", {}).get("properties", {})
            
            print("📋 当前索引字段:")
            has_vector = False
            for field_name, field_config in properties.items():
                field_type = field_config.get("type", "unknown")
                print(f"  {field_name}: {field_type}")
                
                if field_type == "dense_vector":
                    has_vector = True
                    dims = field_config.get("dims", "unknown")
                    print(f"    ✅ 向量字段维度: {dims}")
            
            if not has_vector:
                print("  ❌ 没有发现向量字段 (dense_vector)")
                return False
            else:
                print("  ✅ 发现向量字段")
                return True
                
        else:
            print(f"❌ 获取映射失败: {response.status_code}")
            return False
            
    except Exception as e:
        print(f"❌ 检查映射时出错: {e}")
        return False

def create_vector_mapping():
    """创建包含向量字段的索引映射"""
    
    print("\n" + "=" * 60)
    print("2. 创建向量字段映射")
    print("=" * 60)
    
    # 首先删除现有索引
    delete_url = f"{ES_URL}/{INDEX_NAME}"
    
    try:
        response = requests.delete(delete_url, auth=(USERNAME, PASSWORD))
        if response.status_code in [200, 404]:
            print("✅ 现有索引已删除或不存在")
        else:
            print(f"⚠️ 删除索引响应: {response.status_code}")
    except Exception as e:
        print(f"⚠️ 删除索引时出错: {e}")
    
    # 创建新的索引映射
    create_url = f"{ES_URL}/{INDEX_NAME}"
    mapping = {
        "mappings": {
            "properties": {
                "id": {"type": "keyword"},
                "content": {
                    "type": "text",
                    "analyzer": "ik_max_word",
                    "search_analyzer": "ik_smart"
                },
                "embedding": {
                    "type": "dense_vector",
                    "dims": 1536,
                    "index": True,
                    "similarity": "cosine"
                },
                "metadata": {
                    "properties": {
                        "source": {"type": "keyword"},
                        "latest_question": {
                            "type": "text",
                            "analyzer": "ik_max_word",
                            "search_analyzer": "ik_smart"
                        },
                        "intent": {"type": "keyword"},
                        "processing_method": {"type": "keyword"},
                        "import_date": {"type": "date"},
                        "response_data": {
                            "properties": {
                                "operation": {"type": "keyword"},
                                "parameters": {"type": "object"}
                            }
                        }
                    }
                }
            }
        },
        "settings": {
            "number_of_shards": 1,
            "number_of_replicas": 0,
            "analysis": {
                "analyzer": {
                    "ik_max_word": {
                        "type": "ik_max_word"
                    },
                    "ik_smart": {
                        "type": "ik_smart"
                    }
                }
            }
        }
    }
    
    try:
        response = requests.put(create_url, json=mapping, auth=(USERNAME, PASSWORD))
        
        if response.status_code == 200:
            print("✅ 新索引映射创建成功")
            return True
        else:
            print(f"❌ 创建索引映射失败: {response.status_code}")
            print(f"错误信息: {response.text}")
            return False
            
    except Exception as e:
        print(f"❌ 创建索引映射时出错: {e}")
        return False

def get_embedding_from_openai(text):
    """从OpenAI API获取文本嵌入"""
    
    # 这里需要调用你的Spring Boot应用的嵌入接口
    # 或者直接调用OpenAI API
    
    # 模拟嵌入向量（实际应用中需要真实的嵌入）
    # 这里返回一个1536维的零向量作为占位符
    return [0.0] * 1536

def import_data_with_vectors():
    """重新导入数据并生成向量嵌入"""
    
    print("\n" + "=" * 60)
    print("3. 重新导入数据并生成向量嵌入")
    print("=" * 60)
    
    # 读取Excel文件
    excel_file = "/d:/workspace/rag-elasticsearch/src/main/resources/rag/思考过程补充结果_20251025_095646.xlsx"
    
    try:
        df = pd.read_excel(excel_file, sheet_name="Sheet2")
        print(f"✅ 读取Excel文件成功，共 {len(df)} 行数据")
        
        # 批量导入数据
        bulk_data = []
        
        for index, row in df.iterrows():
            # 解析响应数据
            response_data = {}
            try:
                if pd.notna(row.get('回答', '')):
                    response_data = json.loads(row['回答'])
            except:
                response_data = {"operation": "unknown", "parameters": {}}
            
            # 构建文档内容
            content = f"用户提问: {row.get('最新提问', '')} | 思考过程: {row.get('思考过程', '')} | 用户意图: {row.get('用户意图', '')} | 处理方式: {row.get('处理方式', '')}"
            
            # 获取嵌入向量（这里使用占位符）
            embedding = get_embedding_from_openai(content)
            
            # 构建文档
            doc = {
                "id": f"thinking_process_{index}",
                "content": content,
                "embedding": embedding,
                "metadata": {
                    "source": "思考过程补充结果",
                    "latest_question": row.get('最新提问', ''),
                    "intent": row.get('用户意图', ''),
                    "processing_method": row.get('处理方式', ''),
                    "import_date": datetime.now().isoformat(),
                    "response_data": response_data
                }
            }
            
            # 添加到批量操作
            bulk_data.append({"index": {"_index": INDEX_NAME, "_id": doc["id"]}})
            bulk_data.append(doc)
        
        # 执行批量导入
        bulk_url = f"{ES_URL}/_bulk"
        bulk_body = "\n".join([json.dumps(item) for item in bulk_data]) + "\n"
        
        response = requests.post(
            bulk_url,
            data=bulk_body,
            headers={"Content-Type": "application/x-ndjson"},
            auth=(USERNAME, PASSWORD)
        )
        
        if response.status_code == 200:
            result = response.json()
            errors = result.get("errors", False)
            
            if not errors:
                print(f"✅ 成功导入 {len(df)} 条数据（包含向量嵌入）")
                return True
            else:
                print("❌ 批量导入时出现错误")
                for item in result.get("items", []):
                    if "error" in item.get("index", {}):
                        print(f"  错误: {item['index']['error']}")
                return False
        else:
            print(f"❌ 批量导入失败: {response.status_code}")
            print(f"错误信息: {response.text}")
            return False
            
    except Exception as e:
        print(f"❌ 导入数据时出错: {e}")
        return False

def test_vector_search():
    """测试向量搜索"""
    
    print("\n" + "=" * 60)
    print("4. 测试向量搜索")
    print("=" * 60)
    
    # 模拟查询向量
    query_vector = [0.0] * 1536
    
    search_url = f"{ES_URL}/{INDEX_NAME}/_search"
    query = {
        "query": {
            "script_score": {
                "query": {"match_all": {}},
                "script": {
                    "source": "cosineSimilarity(params.query_vector, 'embedding') + 1.0",
                    "params": {"query_vector": query_vector}
                }
            }
        },
        "size": 5,
        "_source": ["metadata.latest_question", "metadata.intent"]
    }
    
    try:
        response = requests.post(search_url, json=query, auth=(USERNAME, PASSWORD))
        
        if response.status_code == 200:
            data = response.json()
            hits = data.get("hits", {}).get("hits", [])
            total = data.get("hits", {}).get("total", {}).get("value", 0)
            
            print(f"✅ 向量搜索成功，找到 {total} 个文档")
            
            for i, hit in enumerate(hits):
                metadata = hit.get("_source", {}).get("metadata", {})
                score = hit.get("_score", 0)
                print(f"  {i+1}. {metadata.get('latest_question', 'N/A')} (得分: {score:.2f})")
                
            return True
        else:
            print(f"❌ 向量搜索失败: {response.status_code}")
            return False
            
    except Exception as e:
        print(f"❌ 向量搜索时出错: {e}")
        return False

def main():
    """主函数"""
    
    print("🔧 修复向量存储问题")
    print("=" * 60)
    
    # 1. 检查当前映射
    has_vector = check_index_mapping()
    
    if not has_vector:
        # 2. 创建向量映射
        if create_vector_mapping():
            # 3. 重新导入数据
            if import_data_with_vectors():
                # 4. 测试向量搜索
                test_vector_search()
            else:
                print("❌ 数据导入失败")
        else:
            print("❌ 索引映射创建失败")
    else:
        print("✅ 索引已包含向量字段")
        test_vector_search()
    
    print("\n" + "=" * 60)
    print("⚠️  注意：此脚本使用占位符向量，需要集成真实的嵌入服务")
    print("建议通过Spring Boot应用的接口重新导入数据以获得真实向量")
    print("=" * 60)

if __name__ == "__main__":
    main()