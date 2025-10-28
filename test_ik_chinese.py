#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
测试IK分词器对中文的分词效果
"""
import json
import requests

def test_ik_analyzer():
    """测试IK分词器"""
    url = "http://localhost:9200/_analyze"
    
    # 测试中文文本
    test_texts = [
        "我想了解贷款利率",
        "中国",
        "贷款利率",
        "hello world"
    ]
    
    for text in test_texts:
        print(f"\n测试文本: '{text}'")
        
        # 测试IK分词器
        data_ik = {
            "analyzer": "ik_max_word",
            "text": text
        }
        
        try:
            response = requests.post(url, json=data_ik)
            if response.status_code == 200:
                result = response.json()
                tokens = result.get('tokens', [])
                print(f"IK分词器分词数量: {len(tokens)}")
                for token in tokens:
                    print(f"  '{token.get('token', '')}'")
            else:
                print(f"IK分词器测试失败: {response.status_code} - {response.text}")
        except Exception as e:
            print(f"IK分词器测试异常: {e}")
        
        # 测试标准分词器
        data_standard = {
            "analyzer": "standard",
            "text": text
        }
        
        try:
            response = requests.post(url, json=data_standard)
            if response.status_code == 200:
                result = response.json()
                tokens = result.get('tokens', [])
                print(f"标准分词器分词数量: {len(tokens)}")
                for token in tokens:
                    print(f"  '{token.get('token', '')}'")
            else:
                print(f"标准分词器测试失败: {response.status_code} - {response.text}")
        except Exception as e:
            print(f"标准分词器测试异常: {e}")

def test_new_index():
    """测试新创建的索引"""
    print("\n" + "="*50)
    print("测试新索引 'customer_support_qa_ik'")
    print("="*50)
    
    url = "http://localhost:9200/customer_support_qa_ik/_analyze"
    
    test_texts = [
        "我想了解贷款利率",
        "中国",
        "贷款利率"
    ]
    
    for text in test_texts:
        print(f"\n测试文本: '{text}'")
        
        # 测试自定义IK分析器
        data_custom = {
            "analyzer": "ik_analyzer",
            "text": text
        }
        
        try:
            response = requests.post(url, json=data_custom)
            if response.status_code == 200:
                result = response.json()
                tokens = result.get('tokens', [])
                print(f"自定义IK分析器分词数量: {len(tokens)}")
                for token in tokens:
                    print(f"  '{token.get('token', '')}'")
            else:
                print(f"自定义IK分析器测试失败: {response.status_code} - {response.text}")
        except Exception as e:
            print(f"自定义IK分析器测试异常: {e}")

if __name__ == "__main__":
    print("测试IK分词器的中文分词效果")
    print("="*50)
    
    test_ik_analyzer()
    test_new_index()
    
    print("\n" + "="*50)
    print("测试完成")
    print("="*50)