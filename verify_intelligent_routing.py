#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
智能路由验证脚本
验证直接回答模式是否正确绕过LLM调用
"""

import requests
import json
import time

# API配置
BASE_URL = "http://localhost:8082"
CHAT_ENDPOINT = f"{BASE_URL}/api/v2/assistant/chat"

def test_intelligent_routing(query, expected_routing_type=None):
    """
    测试智能路由功能
    
    Args:
        query: 用户查询
        expected_routing_type: 期望的路由类型 (DIRECT_ANSWER, INTENT_ROUTING, DEFAULT_RAG)
    """
    print(f"\n{'='*80}")
    print(f"🔍 测试查询: {query}")
    print(f"📋 期望路由类型: {expected_routing_type}")
    print(f"{'='*80}")
    
    payload = {
        "chatId": "verify-test-001",
        "userMessage": query,
        "userName": "测试用户",
        "availableCredit": 50000,
        "recentRepaymentStatus": "正常"
    }
    
    headers = {
        "Content-Type": "application/json"
    }
    
    try:
        start_time = time.time()
        response = requests.post(CHAT_ENDPOINT, json=payload, headers=headers, timeout=30)
        end_time = time.time()
        
        print(f"⏱️  响应时间: {end_time - start_time:.2f}秒")
        print(f"📊 状态码: {response.status_code}")
        print(f"📄 Content-Type: {response.headers.get('Content-Type', 'N/A')}")
        
        if response.status_code == 200:
            response_text = response.text
            print(f"✅ 响应内容: {response_text}")
            
            # 分析响应特征
            if len(response_text) < 500 and not response_text.startswith("抱歉"):
                print("🎯 特征分析: 可能是直接回答（短且非错误消息）")
            elif "抱歉" in response_text and "系统暂时无法处理" in response_text:
                print("❌ 特征分析: 系统错误消息")
            else:
                print("🤖 特征分析: 可能是LLM生成的回答")
                
            # 检查是否包含模板变量
            if "${" in response_text and "}" in response_text:
                print("⚠️  发现模板变量未替换")
                
        else:
            print(f"❌ 请求失败: {response.text}")
            
    except requests.exceptions.RequestException as e:
        print(f"❌ 网络错误: {e}")

def main():
    """主测试函数"""
    print("🚀 开始智能路由验证测试")
    print("=" * 80)
    
    # 测试用例：应该触发直接回答的查询
    direct_answer_queries = [
        ("贷款利率是多少", "DIRECT_ANSWER"),
        ("如何申请贷款", "DIRECT_ANSWER"),
        ("贷款额度", "DIRECT_ANSWER"),
        ("还款方式", "DIRECT_ANSWER"),
        ("申请条件", "DIRECT_ANSWER")
    ]
    
    # 测试用例：应该触发意图路由的查询
    intent_routing_queries = [
        ("我想申请贷款", "INTENT_ROUTING"),
        ("开始申请流程", "INTENT_ROUTING")
    ]
    
    # 测试用例：应该触发默认RAG的查询
    default_rag_queries = [
        ("今天天气怎么样", "DEFAULT_RAG"),
        ("你是谁", "DEFAULT_RAG"),
        ("随机问题测试", "DEFAULT_RAG")
    ]
    
    all_queries = direct_answer_queries + intent_routing_queries + default_rag_queries
    
    success_count = 0
    total_count = len(all_queries)
    
    for query, expected_type in all_queries:
        try:
            test_intelligent_routing(query, expected_type)
            success_count += 1
        except Exception as e:
            print(f"❌ 测试失败: {e}")
        
        # 短暂延迟避免请求过快
        time.sleep(1)
    
    print(f"\n{'='*80}")
    print(f"📊 测试总结")
    print(f"{'='*80}")
    print(f"✅ 成功测试: {success_count}/{total_count}")
    print(f"📈 成功率: {success_count/total_count*100:.1f}%")
    
    if success_count == total_count:
        print("🎉 所有测试通过！智能路由功能正常工作")
    else:
        print("⚠️  部分测试失败，需要进一步检查")

if __name__ == "__main__":
    main()