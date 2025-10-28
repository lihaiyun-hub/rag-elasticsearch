#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
将Excel文件数据导入到Elasticsearch的脚本
"""

import pandas as pd
import json
import requests
from datetime import datetime
import hashlib
import os

class ElasticsearchImporter:
    def __init__(self, es_host="http://127.0.0.1:9200", username="elastic", password="yingzi"):
        self.es_host = es_host
        self.auth = (username, password)
        self.index_name = "customer_support_qa"
        
    def test_connection(self):
        """测试Elasticsearch连接"""
        try:
            response = requests.get(f"{self.es_host}/_cluster/health", auth=self.auth)
            if response.status_code == 200:
                print("✓ Elasticsearch连接成功")
                return True
            else:
                print(f"✗ Elasticsearch连接失败: {response.status_code}")
                return False
        except Exception as e:
            print(f"✗ Elasticsearch连接异常: {e}")
            return False
    
    def create_document_id(self, content):
        """根据内容创建唯一的文档ID"""
        return hashlib.md5(content.encode('utf-8')).hexdigest()
    
    def prepare_document(self, question, answer, source_file, sheet_name="", additional_fields=None):
        """准备要插入的文档"""
        # 创建基础文档
        doc = {
            "question": question.strip() if pd.notna(question) else "",
            "answer": answer.strip() if pd.notna(answer) else "",
            "source_file": source_file,
            "sheet_name": sheet_name,
            "created_at": datetime.now().isoformat(),
            "content": f"{question} {answer}".strip()  # 用于向量化的完整内容
        }
        
        # 添加额外字段
        if additional_fields:
            doc.update(additional_fields)
        
        return doc
    
    def bulk_insert_documents(self, documents):
        """批量插入文档到Elasticsearch"""
        if not documents:
            print("没有文档需要插入")
            return False
        
        # 构建批量插入的请求体
        bulk_body = []
        for doc in documents:
            doc_id = self.create_document_id(doc["content"])
            
            # 添加索引操作
            bulk_body.append(json.dumps({
                "index": {
                    "_index": self.index_name,
                    "_id": doc_id
                }
            }))
            
            # 添加文档内容
            bulk_body.append(json.dumps(doc, ensure_ascii=False))
        
        bulk_data = "\n".join(bulk_body) + "\n"
        
        try:
            response = requests.post(
                f"{self.es_host}/_bulk",
                headers={"Content-Type": "application/x-ndjson"},
                data=bulk_data.encode('utf-8'),
                auth=self.auth
            )
            
            if response.status_code == 200:
                result = response.json()
                errors = [item for item in result.get("items", []) if "error" in item.get("index", {})]
                
                if errors:
                    print(f"批量插入部分失败，错误数量: {len(errors)}")
                    for error in errors[:5]:  # 只显示前5个错误
                        print(f"错误: {error}")
                else:
                    print(f"✓ 成功插入 {len(documents)} 条文档")
                
                return len(errors) == 0
            else:
                print(f"✗ 批量插入失败: {response.status_code}")
                print(response.text)
                return False
                
        except Exception as e:
            print(f"✗ 批量插入异常: {e}")
            return False
    
    def import_qa_knowledge_base(self, file_path):
        """导入问答知识库.xlsx文件"""
        print(f"\n=== 导入问答知识库: {file_path} ===")
        
        if not os.path.exists(file_path):
            print(f"文件不存在: {file_path}")
            return False
        
        try:
            df = pd.read_excel(file_path, sheet_name="Sheet1")
            print(f"读取到 {len(df)} 行数据")
            
            documents = []
            
            for index, row in df.iterrows():
                question = row.get("最新提问", "")
                answer = row.get("回答", "")
                process_info = row.get("处理", "")
                
                # 跳过空的问答对
                if pd.isna(question) or pd.isna(answer) or not question.strip() or not answer.strip():
                    continue
                
                # 准备文档
                additional_fields = {
                    "process_info": process_info if pd.notna(process_info) else "",
                    "data_type": "qa_knowledge_base"
                }
                
                doc = self.prepare_document(
                    question=question,
                    answer=answer,
                    source_file="问答知识库.xlsx",
                    sheet_name="Sheet1",
                    additional_fields=additional_fields
                )
                
                documents.append(doc)
            
            print(f"准备插入 {len(documents)} 条有效文档")
            return self.bulk_insert_documents(documents)
            
        except Exception as e:
            print(f"导入问答知识库时出错: {e}")
            return False
    
    def import_thinking_process(self, file_path):
        """导入思考过程补充结果文件"""
        print(f"\n=== 导入思考过程补充结果: {file_path} ===")
        
        if not os.path.exists(file_path):
            print(f"文件不存在: {file_path}")
            return False
        
        try:
            # 读取Sheet2
            df = pd.read_excel(file_path, sheet_name="Sheet2")
            print(f"读取到 {len(df)} 行数据")
            
            documents = []
            
            for index, row in df.iterrows():
                question = row.get("最新提问", "")
                answer = row.get("回答", "")
                thinking_process = row.get("思考过程", "")
                process_info = row.get("处理", "")
                intent = row.get("意图", "")
                
                # 跳过空的问答对
                if pd.isna(question) or pd.isna(answer) or not question.strip() or not answer.strip():
                    continue
                
                # 准备文档
                additional_fields = {
                    "thinking_process": thinking_process if pd.notna(thinking_process) else "",
                    "process_info": process_info if pd.notna(process_info) else "",
                    "intent": intent if pd.notna(intent) else "",
                    "data_type": "thinking_process"
                }
                
                doc = self.prepare_document(
                    question=question,
                    answer=answer,
                    source_file="思考过程补充结果_20251025_095646.xlsx",
                    sheet_name="Sheet2",
                    additional_fields=additional_fields
                )
                
                documents.append(doc)
            
            print(f"准备插入 {len(documents)} 条有效文档")
            return self.bulk_insert_documents(documents)
            
        except Exception as e:
            print(f"导入思考过程补充结果时出错: {e}")
            return False
    
    def get_index_stats(self):
        """获取索引统计信息"""
        try:
            response = requests.get(f"{self.es_host}/{self.index_name}/_stats", auth=self.auth)
            if response.status_code == 200:
                stats = response.json()
                doc_count = stats["indices"][self.index_name]["total"]["docs"]["count"]
                print(f"索引 {self.index_name} 当前文档数量: {doc_count}")
                return doc_count
            else:
                print(f"获取索引统计失败: {response.status_code}")
                return None
        except Exception as e:
            print(f"获取索引统计异常: {e}")
            return None

def main():
    """主函数"""
    # 文件路径
    qa_file = r"d:\workspace\rag-elasticsearch\src\main\resources\rag\问答知识库.xlsx"
    thinking_file = r"d:\workspace\rag-elasticsearch\src\main\resources\rag\思考过程补充结果_20251025_095646.xlsx"
    
    # 创建导入器
    importer = ElasticsearchImporter()
    
    # 测试连接
    if not importer.test_connection():
        print("无法连接到Elasticsearch，请检查配置")
        return
    
    # 获取导入前的文档数量
    print("\n=== 导入前状态 ===")
    before_count = importer.get_index_stats()
    
    # 导入问答知识库
    qa_success = importer.import_qa_knowledge_base(qa_file)
    
    # 导入思考过程补充结果
    thinking_success = importer.import_thinking_process(thinking_file)
    
    # 获取导入后的文档数量
    print("\n=== 导入后状态 ===")
    after_count = importer.get_index_stats()
    
    if before_count is not None and after_count is not None:
        added_count = after_count - before_count
        print(f"本次导入新增文档数量: {added_count}")
    
    # 总结
    print("\n=== 导入总结 ===")
    if qa_success and thinking_success:
        print("✓ 所有文件导入成功")
    elif qa_success or thinking_success:
        print("⚠ 部分文件导入成功")
    else:
        print("✗ 导入失败")

if __name__ == "__main__":
    main()