#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
数据迁移脚本：将ai_loan1_ik中的数据迁移到customer_support_qa索引
并重构字段结构
"""

import requests
import json
from datetime import datetime
import time

# Elasticsearch配置
ES_URL = "http://localhost:9200"
USERNAME = "elastic"
PASSWORD = "yingzi"

SOURCE_INDEX = "ai_loan1_ik"
TARGET_INDEX = "customer_support_qa"

class DataMigrator:
    def __init__(self):
        self.session = requests.Session()
        self.session.auth = (USERNAME, PASSWORD)
        self.session.headers.update({'Content-Type': 'application/json'})
    
    def get_all_documents(self, index_name, batch_size=100):
        """获取索引中的所有文档"""
        print(f"📥 开始获取索引 {index_name} 中的所有文档...")
        
        all_docs = []
        scroll_id = None
        
        # 初始搜索
        url = f"{ES_URL}/{index_name}/_search?scroll=5m"
        query = {
            "size": batch_size,
            "query": {"match_all": {}},
            "_source": True
        }
        
        try:
            response = self.session.post(url, json=query)
            if response.status_code != 200:
                print(f"❌ 初始搜索失败: {response.status_code}")
                print(f"错误信息: {response.text}")
                return []
            
            data = response.json()
            scroll_id = data.get('_scroll_id')
            hits = data.get('hits', {}).get('hits', [])
            all_docs.extend(hits)
            
            print(f"📄 获取到第一批 {len(hits)} 个文档")
            
            # 继续滚动获取剩余文档
            while hits:
                scroll_url = f"{ES_URL}/_search/scroll"
                scroll_query = {
                    "scroll": "5m",
                    "scroll_id": scroll_id
                }
                
                response = self.session.post(scroll_url, json=scroll_query)
                if response.status_code != 200:
                    print(f"❌ 滚动搜索失败: {response.status_code}")
                    break
                
                data = response.json()
                scroll_id = data.get('_scroll_id')
                hits = data.get('hits', {}).get('hits', [])
                all_docs.extend(hits)
                
                if hits:
                    print(f"📄 获取到 {len(hits)} 个文档，总计: {len(all_docs)}")
            
            # 清理scroll
            if scroll_id:
                clear_url = f"{ES_URL}/_search/scroll"
                self.session.delete(clear_url, json={"scroll_id": [scroll_id]})
            
            print(f"✅ 总共获取到 {len(all_docs)} 个文档")
            return all_docs
            
        except Exception as e:
            print(f"❌ 获取文档时出错: {e}")
            return []
    
    def transform_document(self, source_doc):
        """转换文档结构"""
        source = source_doc.get('_source', {})
        metadata = source.get('metadata', {})
        
        # 提取字段
        question_text = metadata.get('question_text', '')
        answer = metadata.get('answer', '')
        
        # 如果没有question_text，尝试从content中提取
        if not question_text:
            content = source.get('content', '')
            if content and 'Q:' in content and 'A:' in content:
                lines = content.split('\n')
                for line in lines:
                    if line.strip().startswith('Q:'):
                        question_text = line.replace('Q:', '').strip()
                        break
        
        # 如果没有answer，尝试从content中提取
        if not answer:
            content = source.get('content', '')
            if content and 'Q:' in content and 'A:' in content:
                lines = content.split('\n')
                answer_lines = []
                found_answer = False
                for line in lines:
                    if line.strip().startswith('A:'):
                        found_answer = True
                        answer_lines.append(line.replace('A:', '').strip())
                    elif found_answer and line.strip():
                        answer_lines.append(line.strip())
                answer = ' '.join(answer_lines)
        
        # 构建新的文档结构
        new_doc = {
            "historical_question": "",  # 历史提问为空
            "latest_question": question_text,  # question_text映射为latest_question
            "thinking_process": "",  # 思考过程为空
            "processing_type": "直接回答",  # 处理类型是直接回答
            "reply": answer,  # answer映射为reply（合并了操作和参数）
            "created_at": datetime.now().isoformat(),
            "source": "migrated_from_ai_loan1_ik",
            "original_id": source_doc.get('_id', ''),
            "migration_timestamp": datetime.now().isoformat()
        }
        
        return new_doc
    
    def delete_intent_field(self):
        """删除customer_support_qa索引中的intent字段"""
        print(f"🗑️  开始删除 {TARGET_INDEX} 索引中的intent字段...")
        
        # 使用update_by_query删除intent字段
        url = f"{ES_URL}/{TARGET_INDEX}/_update_by_query"
        query = {
            "conflicts": "proceed",  # 遇到版本冲突时继续处理
            "script": {
                "source": "ctx._source.remove('intent')"
            },
            "query": {
                "exists": {
                    "field": "intent"
                }
            }
        }
        
        try:
            response = self.session.post(url, json=query)
            if response.status_code == 200:
                result = response.json()
                updated = result.get('updated', 0)
                print(f"✅ 成功删除 {updated} 个文档的intent字段")
                return True
            else:
                print(f"❌ 删除intent字段失败: {response.status_code}")
                print(f"错误信息: {response.text}")
                return False
        except Exception as e:
            print(f"❌ 删除intent字段时出错: {e}")
            return False
    
    def merge_operation_parameters(self):
        """合并operation和parameters字段为reply字段"""
        print(f"🔄 开始合并 {TARGET_INDEX} 索引中的operation和parameters字段为reply字段...")
        
        # 使用update_by_query合并字段，添加conflicts参数处理版本冲突
        url = f"{ES_URL}/{TARGET_INDEX}/_update_by_query"
        query = {
            "conflicts": "proceed",  # 遇到版本冲突时继续处理
            "script": {
                "source": """
                if (ctx._source.containsKey('operation') || ctx._source.containsKey('parameters')) {
                    String operation = ctx._source.containsKey('operation') ? ctx._source.operation : '';
                    Object parameters = ctx._source.containsKey('parameters') ? ctx._source.parameters : null;
                    
                    String reply = '';
                    if (operation != null && operation != '') {
                        reply = operation;
                        if (parameters != null) {
                            reply = reply + ' ' + parameters.toString();
                        }
                    } else if (parameters != null) {
                        reply = parameters.toString();
                    }
                    
                    ctx._source.reply = reply;
                    ctx._source.remove('operation');
                    ctx._source.remove('parameters');
                }
                """
            },
            "query": {
                "bool": {
                    "should": [
                        {"exists": {"field": "operation"}},
                        {"exists": {"field": "parameters"}}
                    ]
                }
            }
        }
        
        try:
            response = self.session.post(url, json=query)
            if response.status_code == 200:
                result = response.json()
                updated = result.get('updated', 0)
                print(f"✅ 成功合并 {updated} 个文档的operation和parameters字段")
                return True
            else:
                print(f"❌ 合并字段失败: {response.status_code}")
                print(f"错误信息: {response.text}")
                return False
        except Exception as e:
            print(f"❌ 合并字段时出错: {e}")
            return False
    
    def bulk_insert_documents(self, documents):
        """批量插入文档"""
        if not documents:
            print("⚠️  没有文档需要插入")
            return 0
        
        print(f"📤 开始批量插入 {len(documents)} 个文档到 {TARGET_INDEX}...")
        
        # 构建bulk请求
        bulk_data = []
        for doc in documents:
            # 添加index操作
            bulk_data.append(json.dumps({"index": {"_index": TARGET_INDEX}}))
            # 添加文档数据
            bulk_data.append(json.dumps(doc))
        
        bulk_body = '\n'.join(bulk_data) + '\n'
        
        url = f"{ES_URL}/_bulk"
        headers = {'Content-Type': 'application/x-ndjson'}
        
        try:
            response = self.session.post(url, data=bulk_body, headers=headers)
            if response.status_code == 200:
                result = response.json()
                items = result.get('items', [])
                success_count = 0
                error_count = 0
                
                for item in items:
                    if 'index' in item:
                        if item['index'].get('status') in [200, 201]:
                            success_count += 1
                        else:
                            error_count += 1
                            print(f"❌ 插入失败: {item['index'].get('error', 'Unknown error')}")
                
                print(f"✅ 成功插入 {success_count} 个文档")
                if error_count > 0:
                    print(f"❌ 插入失败 {error_count} 个文档")
                
                return success_count
            else:
                print(f"❌ 批量插入失败: {response.status_code}")
                print(f"错误信息: {response.text}")
                return 0
        except Exception as e:
            print(f"❌ 批量插入时出错: {e}")
            return 0
    
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
                    return doc_count
            return 0
        except Exception as e:
            print(f"❌ 获取索引统计信息时出错: {e}")
            return 0
    
    def migrate_data(self):
        """执行完整的数据迁移流程"""
        print("🚀 开始数据迁移流程...")
        print("=" * 80)
        
        # 1. 获取源索引统计信息
        source_count = self.get_index_stats(SOURCE_INDEX)
        target_count_before = self.get_index_stats(TARGET_INDEX)
        
        print(f"📊 迁移前统计:")
        print(f"   源索引 {SOURCE_INDEX}: {source_count} 个文档")
        print(f"   目标索引 {TARGET_INDEX}: {target_count_before} 个文档")
        print()
        
        # 2. 删除intent字段
        if not self.delete_intent_field():
            print("❌ 删除intent字段失败，终止迁移")
            return False
        print()
        
        # 3. 合并operation和parameters字段
        if not self.merge_operation_parameters():
            print("❌ 合并字段失败，终止迁移")
            return False
        print()
        
        # 4. 获取源索引的所有文档
        source_docs = self.get_all_documents(SOURCE_INDEX)
        if not source_docs:
            print("❌ 无法获取源索引文档，终止迁移")
            return False
        print()
        
        # 5. 转换文档结构
        print("🔄 开始转换文档结构...")
        transformed_docs = []
        for i, doc in enumerate(source_docs):
            try:
                transformed_doc = self.transform_document(doc)
                if transformed_doc['latest_question']:  # 只迁移有问题的文档
                    transformed_docs.append(transformed_doc)
                
                if (i + 1) % 50 == 0:
                    print(f"   已转换 {i + 1}/{len(source_docs)} 个文档")
            except Exception as e:
                print(f"❌ 转换文档 {doc.get('_id', 'unknown')} 时出错: {e}")
        
        print(f"✅ 成功转换 {len(transformed_docs)} 个文档")
        print()
        
        # 6. 批量插入到目标索引
        success_count = self.bulk_insert_documents(transformed_docs)
        print()
        
        # 7. 获取迁移后统计信息
        target_count_after = self.get_index_stats(TARGET_INDEX)
        
        print("📊 迁移后统计:")
        print(f"   目标索引 {TARGET_INDEX}: {target_count_after} 个文档")
        print(f"   新增文档: {target_count_after - target_count_before} 个")
        print()
        
        if success_count > 0:
            print("🎉 数据迁移完成！")
            return True
        else:
            print("❌ 数据迁移失败！")
            return False

def main():
    print("🔄 AI Loan数据迁移工具")
    print("=" * 80)
    print(f"源索引: {SOURCE_INDEX}")
    print(f"目标索引: {TARGET_INDEX}")
    print()
    
    migrator = DataMigrator()
    
    # 确认操作
    print("⚠️  此操作将:")
    print("   1. 删除customer_support_qa索引中的intent字段")
    print("   2. 合并operation和parameters字段为reply字段")
    print("   3. 将ai_loan1_ik中的数据迁移到customer_support_qa")
    print("   4. question_text映射为latest_question")
    print("   5. answer映射为reply")
    print("   6. 处理类型设为'直接回答'")
    print("   7. 思考过程和历史提问设为空")
    print()
    
    # 执行迁移
    success = migrator.migrate_data()
    
    if success:
        print("\n🎉 迁移成功完成！")
    else:
        print("\n❌ 迁移失败！")

if __name__ == "__main__":
    main()