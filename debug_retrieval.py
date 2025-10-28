#!/usr/bin/env python3
"""
调试检索功能的脚本
"""

import requests
import json

def test_retrieval():
    """测试检索功能"""
    base_url = "http://localhost:8082"
    
    # 测试查询
    test_queries = [
        "贷款利率是多少",
        "如何申请贷款",
        "贷款额度",
        "还款方式"
    ]
    
    print("🔍 测试检索功能")
    print("=" * 60)
    
    for query in test_queries:
        print(f"\n📝 测试查询: {query}")
        print("-" * 40)
        
        try:
            # 发送请求
            response = requests.post(
                f"{base_url}/api/v2/assistant/chat",
                json={
                    "chatId": "debug-test-001",
                    "userMessage": query,
                    "userName": "测试用户",
                    "availableCredit": 50000,
                    "recentRepaymentStatus": "正常"
                },
                headers={"Content-Type": "application/json"},
                timeout=10
            )
            
            print(f"状态码: {response.status_code}")
            print(f"响应头: {dict(response.headers)}")
            print(f"原始响应内容: {response.text}")
            
            if response.status_code == 200:
                try:
                    result = response.json()
                    print(f"JSON响应: {result}")
                except Exception as e:
                    print(f"JSON解析失败: {e}")
            else:
                print(f"请求失败")
                
        except requests.exceptions.RequestException as e:
            print(f"请求失败: {e}")

if __name__ == "__main__":
    test_retrieval()