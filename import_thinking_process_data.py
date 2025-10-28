#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
导入思考过程补充结果Excel数据到Elasticsearch
"""

import pandas as pd
import json
import requests
from datetime import datetime
import uuid
import logging

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# Elasticsearch配置
ES_URL = "http://localhost:9200"
USERNAME = "elastic"
PASSWORD = "yingzi"
INDEX_NAME = "customer_support_qa"

def parse_json_response(response_str):
    """解析回答字段中的JSON字符串"""
    try:
        if pd.isna(response_str) or response_str == "":
            return {}
        
        # 清理字符串
        response_str = str(response_str).strip()
        
        # 尝试解析JSON
        return json.loads(response_str)
    except json.JSONDecodeError as e:
        logger.warning(f"JSON解析失败: {response_str}, 错误: {e}")
        return {"raw_response": response_str}
    except Exception as e:
        logger.warning(f"解析回答字段时出错: {e}")
        return {}

def create_document_from_row(row, index):
    """从Excel行数据创建ES文档"""
    
    # 解析JSON回答
    response_data = parse_json_response(row.get('回答', ''))
    
    # 构建文档内容
    content_parts = []
    
    # 添加最新提问
    latest_question = row.get('最新提问', '')
    if pd.notna(latest_question) and latest_question.strip():
        content_parts.append(f"用户提问: {latest_question}")
    
    # 添加思考过程
    thinking_process = row.get('思考过程', '')
    if pd.notna(thinking_process) and thinking_process.strip():
        content_parts.append(f"思考过程: {thinking_process}")
    
    # 添加意图
    intent = row.get('意图', '')
    if pd.notna(intent) and intent.strip():
        content_parts.append(f"用户意图: {intent}")
    
    # 添加处理方式
    processing = row.get('处理', '')
    if pd.notna(processing) and processing.strip():
        content_parts.append(f"处理方式: {processing}")
    
    # 合并内容
    content = " | ".join(content_parts)
    
    # 构建元数据
    metadata = {
        "source": "思考过程补充结果",
        "data_type": "thinking_process",
        "import_date": datetime.now().isoformat(),
        "row_index": index + 1,
        "latest_question": latest_question,
        "thinking_process": thinking_process,
        "processing_method": processing,
        "intent": intent,
        "response_data": response_data
    }
    
    # 添加历史提问（如果存在）
    history_question = row.get('历史提问', '')
    if pd.notna(history_question) and history_question.strip():
        metadata["history_question"] = history_question
        content = f"历史提问: {history_question} | " + content
    
    # 构建ES文档
    document = {
        "content": content,
        "metadata": metadata
    }
    
    return document

def import_excel_to_elasticsearch(excel_file_path):
    """导入Excel数据到Elasticsearch"""
    
    try:
        logger.info(f"开始导入Excel文件: {excel_file_path}")
        
        # 读取Excel文件
        df = pd.read_excel(excel_file_path, sheet_name='Sheet2')
        logger.info(f"读取到 {len(df)} 行数据")
        
        # 检查索引是否存在
        index_url = f"{ES_URL}/{INDEX_NAME}"
        response = requests.head(index_url, auth=(USERNAME, PASSWORD))
        
        if response.status_code != 200:
            logger.error(f"索引 {INDEX_NAME} 不存在，请先创建索引")
            return False
        
        logger.info(f"索引 {INDEX_NAME} 存在，开始导入数据")
        
        # 批量导入数据
        success_count = 0
        error_count = 0
        
        for index, row in df.iterrows():
            try:
                # 跳过空行
                if pd.isna(row.get('最新提问', '')) or row.get('最新提问', '').strip() == '':
                    logger.warning(f"跳过第 {index + 1} 行：最新提问为空")
                    continue
                
                # 创建文档
                document = create_document_from_row(row, index)
                
                # 生成文档ID
                doc_id = f"thinking_process_{uuid.uuid4().hex[:8]}_{index + 1}"
                
                # 导入到ES
                doc_url = f"{ES_URL}/{INDEX_NAME}/_doc/{doc_id}"
                response = requests.put(
                    doc_url,
                    json=document,
                    headers={"Content-Type": "application/json"},
                    auth=(USERNAME, PASSWORD)
                )
                
                if response.status_code in [200, 201]:
                    success_count += 1
                    logger.info(f"成功导入第 {index + 1} 行数据，文档ID: {doc_id}")
                else:
                    error_count += 1
                    logger.error(f"导入第 {index + 1} 行失败: {response.status_code} - {response.text}")
                
            except Exception as e:
                error_count += 1
                logger.error(f"处理第 {index + 1} 行时出错: {e}")
        
        logger.info(f"导入完成！成功: {success_count}, 失败: {error_count}")
        
        # 刷新索引
        refresh_url = f"{ES_URL}/{INDEX_NAME}/_refresh"
        requests.post(refresh_url, auth=(USERNAME, PASSWORD))
        logger.info("索引刷新完成")
        
        return success_count > 0
        
    except Exception as e:
        logger.error(f"导入过程中出错: {e}")
        return False

def verify_import_result():
    """验证导入结果"""
    try:
        # 查询导入的数据
        search_url = f"{ES_URL}/{INDEX_NAME}/_search"
        query = {
            "query": {
                "bool": {
                    "must": [
                        {"term": {"metadata.source.keyword": "思考过程补充结果"}}
                    ]
                }
            },
            "size": 10,
            "sort": [{"metadata.import_date": {"order": "desc"}}]
        }
        
        response = requests.post(
            search_url,
            json=query,
            headers={"Content-Type": "application/json"},
            auth=(USERNAME, PASSWORD)
        )
        
        if response.status_code == 200:
            data = response.json()
            hits = data.get("hits", {}).get("hits", [])
            total = data.get("hits", {}).get("total", {}).get("value", 0)
            
            logger.info(f"验证结果：找到 {total} 个思考过程数据文档")
            
            if hits:
                logger.info("最新导入的文档示例:")
                for i, hit in enumerate(hits[:3]):
                    source = hit.get("_source", {})
                    content = source.get("content", "")[:100] + "..."
                    metadata = source.get("metadata", {})
                    logger.info(f"  文档 {i+1}: {content}")
                    logger.info(f"    意图: {metadata.get('intent', 'N/A')}")
                    logger.info(f"    导入时间: {metadata.get('import_date', 'N/A')}")
            
            return True
        else:
            logger.error(f"验证查询失败: {response.status_code} - {response.text}")
            return False
            
    except Exception as e:
        logger.error(f"验证导入结果时出错: {e}")
        return False

if __name__ == "__main__":
    # Excel文件路径
    excel_file_path = r"d:\workspace\rag-elasticsearch\src\main\resources\rag\思考过程补充结果_20251025_095646.xlsx"
    
    print("=" * 60)
    print("思考过程补充结果数据导入工具")
    print("=" * 60)
    
    # 导入数据
    if import_excel_to_elasticsearch(excel_file_path):
        print("\n✅ 数据导入成功！")
        
        # 验证导入结果
        print("\n🔍 验证导入结果...")
        if verify_import_result():
            print("✅ 验证通过！数据已成功导入到Elasticsearch")
        else:
            print("❌ 验证失败，请检查数据")
    else:
        print("❌ 数据导入失败，请检查错误日志")