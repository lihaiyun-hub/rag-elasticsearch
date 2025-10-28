#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
验证Excel数据导入结果的脚本
"""

import requests
import json

class ImportVerifier:
    def __init__(self, es_host="http://127.0.0.1:9200", username="elastic", password="yingzi"):
        self.es_host = es_host
        self.auth = (username, password)
        self.index_name = "customer_support_qa"
    
    def search_by_source_file(self, source_file):
        """根据源文件搜索文档"""
        query = {
            "query": {
                "term": {
                    "source_file.keyword": source_file
                }
            },
            "size": 0,  # 只获取数量，不返回具体文档
            "aggs": {
                "data_types": {
                    "terms": {
                        "field": "data_type.keyword"
                    }
                }
            }
        }
        
        try:
            response = requests.post(
                f"{self.es_host}/{self.index_name}/_search",
                headers={"Content-Type": "application/json"},
                data=json.dumps(query),
                auth=self.auth
            )
            
            if response.status_code == 200:
                result = response.json()
                total_hits = result["hits"]["total"]["value"]
                data_types = result.get("aggregations", {}).get("data_types", {}).get("buckets", [])
                
                print(f"源文件 '{source_file}' 的文档数量: {total_hits}")
                if data_types:
                    print("数据类型分布:")
                    for bucket in data_types:
                        print(f"  - {bucket['key']}: {bucket['doc_count']} 条")
                
                return total_hits
            else:
                print(f"搜索失败: {response.status_code}")
                return 0
                
        except Exception as e:
            print(f"搜索异常: {e}")
            return 0
    
    def get_sample_documents(self, source_file, size=3):
        """获取样本文档"""
        query = {
            "query": {
                "term": {
                    "source_file.keyword": source_file
                }
            },
            "size": size
        }
        
        try:
            response = requests.post(
                f"{self.es_host}/{self.index_name}/_search",
                headers={"Content-Type": "application/json"},
                data=json.dumps(query),
                auth=self.auth
            )
            
            if response.status_code == 200:
                result = response.json()
                hits = result["hits"]["hits"]
                
                print(f"\n源文件 '{source_file}' 的样本文档:")
                for i, hit in enumerate(hits, 1):
                    source = hit["_source"]
                    print(f"\n样本 {i}:")
                    print(f"  问题: {source.get('question', '')[:100]}...")
                    print(f"  回答: {source.get('answer', '')[:100]}...")
                    print(f"  数据类型: {source.get('data_type', '')}")
                    print(f"  创建时间: {source.get('created_at', '')}")
                
                return hits
            else:
                print(f"获取样本文档失败: {response.status_code}")
                return []
                
        except Exception as e:
            print(f"获取样本文档异常: {e}")
            return []
    
    def get_total_stats(self):
        """获取总体统计"""
        query = {
            "size": 0,
            "aggs": {
                "source_files": {
                    "terms": {
                        "field": "source_file.keyword",
                        "size": 10
                    }
                },
                "data_types": {
                    "terms": {
                        "field": "data_type.keyword",
                        "size": 10
                    }
                }
            }
        }
        
        try:
            response = requests.post(
                f"{self.es_host}/{self.index_name}/_search",
                headers={"Content-Type": "application/json"},
                data=json.dumps(query),
                auth=self.auth
            )
            
            if response.status_code == 200:
                result = response.json()
                total_docs = result["hits"]["total"]["value"]
                source_files = result.get("aggregations", {}).get("source_files", {}).get("buckets", [])
                data_types = result.get("aggregations", {}).get("data_types", {}).get("buckets", [])
                
                print(f"\n=== 索引总体统计 ===")
                print(f"总文档数量: {total_docs}")
                
                print("\n按源文件分布:")
                for bucket in source_files:
                    print(f"  - {bucket['key']}: {bucket['doc_count']} 条")
                
                print("\n按数据类型分布:")
                for bucket in data_types:
                    print(f"  - {bucket['key']}: {bucket['doc_count']} 条")
                
                return total_docs
            else:
                print(f"获取统计失败: {response.status_code}")
                return 0
                
        except Exception as e:
            print(f"获取统计异常: {e}")
            return 0

def main():
    """主函数"""
    verifier = ImportVerifier()
    
    # 获取总体统计
    total_docs = verifier.get_total_stats()
    
    # 检查问答知识库数据
    print(f"\n=== 验证问答知识库数据 ===")
    qa_count = verifier.search_by_source_file("问答知识库.xlsx")
    verifier.get_sample_documents("问答知识库.xlsx", 2)
    
    # 检查思考过程数据
    print(f"\n=== 验证思考过程数据 ===")
    thinking_count = verifier.search_by_source_file("思考过程补充结果_20251025_095646.xlsx")
    verifier.get_sample_documents("思考过程补充结果_20251025_095646.xlsx", 2)
    
    # 总结
    print(f"\n=== 验证总结 ===")
    print(f"问答知识库文档数量: {qa_count}")
    print(f"思考过程文档数量: {thinking_count}")
    print(f"总文档数量: {total_docs}")

if __name__ == "__main__":
    main()