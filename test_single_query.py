#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
单个查询测试脚本
"""

import requests
import json

# API配置
BASE_URL = "http://localhost:8082"
CHAT_ENDPOINT = f"{BASE_URL}/api/v2/assistant/chat"

def test_single_query():
    """测试单个查询"""
    
    payload = {
        "chatId": "single-test-001",
        "userMessage": "贷款额度",
        "userName": "测试用户",
        "availableCredit": 50000,
        "recentRepaymentStatus": "正常"
    }
    
    headers = {
        "Content-Type": "application/json"
    }
    
    try:
        response = requests.post(CHAT_ENDPOINT, json=payload, headers=headers, timeout=30)
        
        print(f"状态码: {response.status_code}")
        print(f"响应头: {dict(response.headers)}")
        print(f"原始响应: {response.text}")
        
        if response.status_code == 200:
            print("✅ 请求成功")
        else:
            print("❌ 请求失败")
            
    except Exception as e:
        print(f"❌ 错误: {e}")

if __name__ == "__main__":
    test_single_query()