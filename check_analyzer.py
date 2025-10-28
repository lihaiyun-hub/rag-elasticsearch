#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from elasticsearch import Elasticsearch
import json

def check_analyzer():
    """检查ES索引的分词器配置"""
    
    es = Elasticsearch(['http://localhost:9200'])
    index_name = 'customer_support_qa'
    
    print("=== 索引设置 ===")
    try:
        settings = es.indices.get_settings(index=index_name)
        index_settings = settings[index_name]['settings']
        print(json.dumps(index_settings, indent=2, ensure_ascii=False))
    except Exception as e:
        print(f"获取索引设置失败: {e}")
    
    print("\n=== 索引映射详情 ===")
    try:
        mapping = es.indices.get_mapping(index=index_name)
        properties = mapping[index_name]['mappings']['properties']
        
        for field, config in properties.items():
            if config.get('type') == 'text':
                analyzer = config.get('analyzer', '默认')
                print(f"{field}: {config.get('type')} (分词器: {analyzer})")
            else:
                print(f"{field}: {config.get('type', 'object')}")
    except Exception as e:
        print(f"获取索引映射失败: {e}")
    
    print("\n=== 测试分词器 ===")
    try:
        # 测试默认分词器
        test_text = "我想借5000元分6期"
        
        # 测试标准分词器
        result_standard = es.indices.analyze(
            index=index_name,
            body={
                "analyzer": "standard",
                "text": test_text
            }
        )
        print(f"标准分词器结果: {[token['token'] for token in result_standard['tokens']]}")
        
        # 测试IK分词器（如果存在）
        try:
            result_ik = es.indices.analyze(
                index=index_name,
                body={
                    "analyzer": "ik_max_word",
                    "text": test_text
                }
            )
            print(f"IK分词器结果: {[token['token'] for token in result_ik['tokens']]}")
        except Exception as ik_e:
            print(f"IK分词器不可用: {ik_e}")
            
    except Exception as e:
        print(f"测试分词器失败: {e}")

if __name__ == "__main__":
    check_analyzer()