#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
验证数据迁移结果的脚本
"""

import requests
import json
from datetime import datetime

# Elasticsearch配置
ES_URL = "http://localhost:9200"
USERNAME = "elastic"
PASSWORD = "yingzi"

TARGET_INDEX = "customer_support_qa"

class MigrationVerifier:
    def __init__(self):
        self.session = requests.Session()
        self.session.auth = (USERNAME, PASSWORD)
        self.session.headers.update({'Content-Type': 'application/json'})
    
    def get_index_mapping(self, index_name):
        """获取索引映射"""
        url = f"{ES_URL}/{index_name}/_mapping"
        
        try:
            response = self.session.get(url)
            if response.status_code == 200:
                return response.json()
            else:
                print(f"❌ 获取索引映射失败: {response.status_code}")
                return None
        except Exception as e:
            print(f"❌ 获取索引映射时出错: {e}")
            return None
    
    def get_index_stats(self, index_name):
        """获取索引统计信息"""
        url = f"{ES_URL}/{index_name}/_stats"
        
        try:
            response = self.session.get(url)
            if response.status_code == 200:
                data = response.json()
                indices = data.get('indices', {})
                if index_name in indices:
                    total = indices[index_name].get('total', {})
                    doc_count = total.get('docs', {}).get('count', 0)
                    store_size = total.get('store', {}).get('size_in_bytes', 0)
                    return doc_count, store_size
            return 0, 0
        except Exception as e:
            print(f"❌ 获取索引统计信息时出错: {e}")
            return 0, 0
    
    def search_documents(self, index_name, query, size=10):
        """搜索文档"""
        url = f"{ES_URL}/{index_name}/_search"
        search_query = {
            "size": size,
            "query": query,
            "_source": True
        }
        
        try:
            response = self.session.post(url, json=search_query)
            if response.status_code == 200:
                data = response.json()
                hits = data.get('hits', {}).get('hits', [])
                total = data.get('hits', {}).get('total', {})
                if isinstance(total, dict):
                    total_count = total.get('value', 0)
                else:
                    total_count = total
                return hits, total_count
            else:
                print(f"❌ 搜索失败: {response.status_code}")
                return [], 0
        except Exception as e:
            print(f"❌ 搜索时出错: {e}")
            return [], 0
    
    def check_field_exists(self, index_name, field_name):
        """检查字段是否存在"""
        query = {"exists": {"field": field_name}}
        hits, total = self.search_documents(index_name, query, size=1)
        return total > 0
    
    def get_sample_documents(self, index_name, size=5):
        """获取示例文档"""
        query = {"match_all": {}}
        hits, total = self.search_documents(index_name, query, size)
        return hits, total
    
    def verify_migration(self):
        """验证迁移结果"""
        print("🔍 开始验证数据迁移结果...")
        print("=" * 80)
        
        # 1. 获取索引统计信息
        doc_count, store_size = self.get_index_stats(TARGET_INDEX)
        print(f"📊 索引统计信息:")
        print(f"   索引名称: {TARGET_INDEX}")
        print(f"   文档数量: {doc_count}")
        print(f"   存储大小: {store_size / (1024*1024):.2f} MB")
        print()
        
        # 2. 检查字段结构
        print("🔍 检查字段结构:")
        
        # 检查是否还有intent字段
        has_intent = self.check_field_exists(TARGET_INDEX, "intent")
        print(f"   intent字段存在: {'❌ 是' if has_intent else '✅ 否'}")
        
        # 检查是否还有operation字段
        has_operation = self.check_field_exists(TARGET_INDEX, "operation")
        print(f"   operation字段存在: {'❌ 是' if has_operation else '✅ 否'}")
        
        # 检查是否还有parameters字段
        has_parameters = self.check_field_exists(TARGET_INDEX, "parameters")
        print(f"   parameters字段存在: {'❌ 是' if has_parameters else '✅ 否'}")
        
        # 检查是否有reply字段
        has_reply = self.check_field_exists(TARGET_INDEX, "reply")
        print(f"   reply字段存在: {'✅ 是' if has_reply else '❌ 否'}")
        
        # 检查是否有latest_question字段
        has_latest_question = self.check_field_exists(TARGET_INDEX, "latest_question")
        print(f"   latest_question字段存在: {'✅ 是' if has_latest_question else '❌ 否'}")
        
        # 检查是否有processing_type字段
        has_processing_type = self.check_field_exists(TARGET_INDEX, "processing_type")
        print(f"   processing_type字段存在: {'✅ 是' if has_processing_type else '❌ 否'}")
        print()
        
        # 3. 检查迁移的数据
        print("🔍 检查迁移的数据:")
        
        # 搜索来自ai_loan1_ik的数据
        migrated_query = {"term": {"source": "migrated_from_ai_loan1_ik"}}
        migrated_hits, migrated_total = self.search_documents(TARGET_INDEX, migrated_query, size=3)
        print(f"   迁移的文档数量: {migrated_total}")
        
        if migrated_hits:
            print("   迁移文档示例:")
            for i, hit in enumerate(migrated_hits[:3], 1):
                source = hit.get('_source', {})
                print(f"     示例 {i}:")
                print(f"       latest_question: {source.get('latest_question', 'N/A')[:50]}...")
                print(f"       reply: {source.get('reply', 'N/A')[:50]}...")
                print(f"       processing_type: {source.get('processing_type', 'N/A')}")
                print(f"       historical_question: '{source.get('historical_question', 'N/A')}'")
                print(f"       thinking_process: '{source.get('thinking_process', 'N/A')}'")
                print()
        
        # 4. 检查原有Excel数据
        print("🔍 检查原有Excel数据:")
        
        # 搜索Excel来源的数据
        excel_query = {"bool": {"must_not": {"term": {"source": "migrated_from_ai_loan1_ik"}}}}
        excel_hits, excel_total = self.search_documents(TARGET_INDEX, excel_query, size=3)
        print(f"   Excel数据文档数量: {excel_total}")
        
        if excel_hits:
            print("   Excel数据示例:")
            for i, hit in enumerate(excel_hits[:3], 1):
                source = hit.get('_source', {})
                print(f"     示例 {i}:")
                print(f"       latest_question: {source.get('latest_question', 'N/A')[:50]}...")
                print(f"       reply: {source.get('reply', 'N/A')[:50]}...")
                print(f"       processing_type: {source.get('processing_type', 'N/A')}")
                print()
        
        # 5. 总结
        print("📋 迁移结果总结:")
        print(f"   ✅ 总文档数: {doc_count}")
        print(f"   ✅ 迁移文档数: {migrated_total}")
        print(f"   ✅ Excel文档数: {excel_total}")
        print(f"   {'✅' if not has_intent else '❌'} intent字段已删除")
        print(f"   {'✅' if not has_operation else '❌'} operation字段已删除")
        print(f"   {'✅' if not has_parameters else '❌'} parameters字段已删除")
        print(f"   {'✅' if has_reply else '❌'} reply字段已创建")
        print(f"   {'✅' if has_latest_question else '❌'} latest_question字段已创建")
        print(f"   {'✅' if has_processing_type else '❌'} processing_type字段已创建")
        
        if (not has_intent and not has_operation and not has_parameters and 
            has_reply and has_latest_question and has_processing_type and 
            migrated_total > 0):
            print("\n🎉 数据迁移验证成功！")
            return True
        else:
            print("\n❌ 数据迁移验证失败！")
            return False

def main():
    print("🔍 数据迁移结果验证工具")
    print("=" * 80)
    
    verifier = MigrationVerifier()
    success = verifier.verify_migration()
    
    if success:
        print("\n🎉 验证完成，迁移成功！")
    else:
        print("\n❌ 验证完成，发现问题！")

if __name__ == "__main__":
    main()