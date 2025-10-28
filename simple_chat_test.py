#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
简单聊天测试脚本
"""

import requests
import json

# API配置
BASE_URL = "http://localhost:8080"
API_ENDPOINT = f"{BASE_URL}/api/v2/assistant/chat"

def test_simple_chat():
    """测试简单聊天功能"""
    
    # 构建请求数据
    request_data = {
        "chatId": "simple-test-001",
        "userMessage": "你好",
        "userName": "测试用户"
    }
    
    print("发送简单聊天请求...")
    print(f"请求数据: {json.dumps(request_data, ensure_ascii=False, indent=2)}")
    
    try:
        response = requests.post(
            API_ENDPOINT,
            json=request_data,
            headers={"Content-Type": "application/json"},
            timeout=30
        )
        
        print(f"响应状态码: {response.status_code}")
        print(f"响应内容: {response.text}")
        
        if response.status_code == 200:
            print("✅ 聊天功能正常")
        else:
            print("❌ 聊天功能异常")
            
    except Exception as e:
        print(f"❌ 请求异常: {e}")

if __name__ == "__main__":
    test_simple_chat()