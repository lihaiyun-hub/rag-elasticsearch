#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
将Excel数据导入Elasticsearch并进行嵌入向量化
"""

import pandas as pd
import json
import requests
from elasticsearch import Elasticsearch
from sentence_transformers import SentenceTransformer
import numpy as np
from datetime import datetime
import logging

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class ExcelToESImporter:
    def __init__(self, es_host='localhost', es_port=9200, model_name='all-MiniLM-L6-v2'):
        """
        初始化导入器
        """
        # 配置ES客户端
        self.es = Elasticsearch([f'http://{es_host}:{es_port}'])
        self.model = SentenceTransformer(model_name)
        self.index_name = 'customer_support_qa'
        
    def create_index_if_not_exists(self):
        """
        创建ES索引（如果不存在）
        """
        index_mapping = {
            "mappings": {
                "properties": {
                    "historical_question": {"type": "text", "analyzer": "ik_max_word"},
                    "latest_question": {"type": "text", "analyzer": "ik_max_word"},
                    "thinking_process": {"type": "text", "analyzer": "ik_max_word"},
                    "processing_type": {"type": "keyword"},
                    "intent": {"type": "keyword"},
                    "response": {"type": "text"},
                    "operation": {"type": "keyword"},
                    "parameters": {"type": "object"},
                    "question_embedding": {
                        "type": "dense_vector",
                        "dims": 384  # all-MiniLM-L6-v2的向量维度
                    },
                    "created_at": {"type": "date"},
                    "source": {"type": "keyword"}
                }
            }
        }
        
        if not self.es.indices.exists(index=self.index_name):
            self.es.indices.create(index=self.index_name, body=index_mapping)
            logger.info(f"创建索引: {self.index_name}")
        else:
            logger.info(f"索引已存在: {self.index_name}")
    
    def parse_response_json(self, response_str):
        """
        解析回答中的JSON字符串
        """
        try:
            if pd.isna(response_str) or not response_str.strip():
                return None, None
            
            # 清理JSON字符串
            response_str = response_str.strip()
            if not response_str.startswith('{'):
                return None, None
                
            response_json = json.loads(response_str)
            operation = response_json.get('operation', '')
            parameters = response_json.get('parameters', {})
            
            return operation, parameters
        except (json.JSONDecodeError, Exception) as e:
            logger.warning(f"解析JSON失败: {response_str}, 错误: {e}")
            return None, None
    
    def clean_parameter_value(self, value):
        """
        清理参数值，去除空格和空值
        """
        if pd.isna(value) or value is None:
            return ""
        
        value_str = str(value).strip()
        if value_str in ['', ' ', 'NaN', 'nan']:
            return ""
        
        return value_str
    
    def embed_text(self, text):
        """
        对文本进行嵌入向量化
        """
        if pd.isna(text) or not text.strip():
            return None
        
        try:
            embedding = self.model.encode(text.strip())
            return embedding.tolist()
        except Exception as e:
            logger.error(f"嵌入向量化失败: {text}, 错误: {e}")
            return None
    
    def import_excel_data(self, excel_path, sheet_name='Sheet2'):
        """
        导入Excel数据到ES
        """
        logger.info(f"开始导入Excel数据: {excel_path}")
        
        # 读取Excel数据
        df = pd.read_excel(excel_path, sheet_name=sheet_name)
        logger.info(f"读取到 {len(df)} 行数据")
        
        # 创建索引
        self.create_index_if_not_exists()
        
        success_count = 0
        error_count = 0
        
        for index, row in df.iterrows():
            try:
                # 提取基本字段
                historical_question = row.get('历史提问', '')
                latest_question = row.get('最新提问', '')
                thinking_process = row.get('思考过程', '')
                processing_type = row.get('处理', '')
                intent = row.get('意图', '')
                response = row.get('回答', '')
                
                # 清理空值
                if pd.isna(historical_question):
                    historical_question = ""
                if pd.isna(latest_question) or not latest_question.strip():
                    logger.warning(f"第{index+1}行缺少最新提问，跳过")
                    continue
                
                # 解析JSON回答
                operation, parameters = self.parse_response_json(response)
                
                # 清理参数值
                if parameters:
                    cleaned_params = {}
                    for key, value in parameters.items():
                        cleaned_params[key] = self.clean_parameter_value(value)
                    parameters = cleaned_params
                
                # 对最新提问进行嵌入
                question_embedding = self.embed_text(latest_question)
                
                # 构建ES文档
                doc = {
                    "historical_question": historical_question.strip(),
                    "latest_question": latest_question.strip(),
                    "thinking_process": thinking_process.strip() if not pd.isna(thinking_process) else "",
                    "processing_type": processing_type.strip() if not pd.isna(processing_type) else "",
                    "intent": intent.strip() if not pd.isna(intent) else "",
                    "response": response.strip() if not pd.isna(response) else "",
                    "operation": operation if operation else "",
                    "parameters": parameters if parameters else {},
                    "question_embedding": question_embedding,
                    "created_at": datetime.now().isoformat(),
                    "source": "excel_import"
                }
                
                # 插入ES
                result = self.es.index(
                    index=self.index_name,
                    body=doc
                )
                
                success_count += 1
                logger.info(f"成功导入第{index+1}行: {latest_question[:50]}...")
                
            except Exception as e:
                error_count += 1
                logger.error(f"导入第{index+1}行失败: {e}")
        
        logger.info(f"导入完成 - 成功: {success_count}, 失败: {error_count}")
        
        # 刷新索引
        self.es.indices.refresh(index=self.index_name)
        
        return success_count, error_count
    
    def search_similar_questions(self, query, top_k=5):
        """
        搜索相似问题
        """
        try:
            # 对查询进行嵌入
            query_embedding = self.embed_text(query)
            if not query_embedding:
                return []
            
            # ES向量搜索
            search_body = {
                "query": {
                    "script_score": {
                        "query": {"match_all": {}},
                        "script": {
                            "source": "cosineSimilarity(params.query_vector, 'question_embedding') + 1.0",
                            "params": {"query_vector": query_embedding}
                        }
                    }
                },
                "size": top_k,
                "_source": ["latest_question", "intent", "operation", "parameters"]
            }
            
            response = self.es.search(index=self.index_name, body=search_body)
            
            results = []
            for hit in response['hits']['hits']:
                results.append({
                    "question": hit['_source']['latest_question'],
                    "intent": hit['_source']['intent'],
                    "operation": hit['_source']['operation'],
                    "parameters": hit['_source']['parameters'],
                    "score": hit['_score']
                })
            
            return results
            
        except Exception as e:
            logger.error(f"搜索失败: {e}")
            return []

def main():
    """
    主函数
    """
    # Excel文件路径
    excel_path = r'd:\workspace\rag-elasticsearch\src\main\resources\rag\思考过程补充结果_20251025_095646.xlsx'
    
    # 创建导入器
    importer = ExcelToESImporter()
    
    # 导入数据
    success_count, error_count = importer.import_excel_data(excel_path)
    
    print(f"\n=== 导入结果 ===")
    print(f"成功导入: {success_count} 条")
    print(f"导入失败: {error_count} 条")
    
    # 测试搜索功能
    print(f"\n=== 测试搜索功能 ===")
    test_queries = ["借5000", "分期", "借款用途"]
    
    for query in test_queries:
        print(f"\n搜索: '{query}'")
        results = importer.search_similar_questions(query, top_k=3)
        for i, result in enumerate(results, 1):
            print(f"  {i}. {result['question']} (相似度: {result['score']:.3f})")
            print(f"     操作: {result['operation']}, 参数: {result['parameters']}")

if __name__ == "__main__":
    main()