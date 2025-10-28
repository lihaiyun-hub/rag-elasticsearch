#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
快速测试CustomerSupportAssistantV2接口
"""

import requests
import json
import uuid
from datetime import datetime

# 配置
API_URL = "http://localhost:8080/api/v2/assistant/chat"

def create_test_request(user_message="我想借5万元，分12期"):
    """创建测试请求数据"""
    chat_id = f"test_{uuid.uuid4().hex[:8]}"
    
    request_data = {
        "chatId": chat_id,
        "userMessage": user_message,
        "userName": "张三",
        "availableCredit": 50000.0,
        "recentRepaymentStatus": "正常",
        "authorized": True,
        "termOptions": [3, 6, 9, 12],
        "loanPurposes": ["消费", "装修", "教育", "旅游"],
        "bankCardNumber": "1234",
        "bankName": "工商银行"
    }
    
    return request_data

def send_request(request_data):
    """发送请求"""
    headers = {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
    }
    
    print("📤 发送请求到:", API_URL)
    print("📋 请求数据:")
    print(json.dumps(request_data, ensure_ascii=False, indent=2))
    print("-" * 50)
    
    try:
        response = requests.post(API_URL, json=request_data, headers=headers)
        
        print(f"📊 状态码: {response.status_code}")
        print("📄 响应内容:")
        
        if response.headers.get('content-type', '').startswith('application/json'):
            try:
                response_json = response.json()
                print(json.dumps(response_json, ensure_ascii=False, indent=2))
            except:
                print(response.text)
        else:
            print(response.text)
        
        return response
        
    except requests.exceptions.ConnectionError:
        print("❌ 连接失败: 请确保服务器正在运行在 http://localhost:8080")
    except Exception as e:
        print(f"❌ 请求异常: {e}")
    
    return None

def main():
    print("🚀 快速测试CustomerSupportAssistantV2接口")
    print("=" * 60)
    
    # 示例1: 借款方案生成
    print("\n🔹 测试1: 借款方案生成")
    request1 = create_test_request("我想借5万元，分12期，用于装修")
    send_request(request1)
    
    # 示例2: 澄清请求
    print("\n🔹 测试2: 澄清请求")
    request2 = create_test_request("我想借钱")
    send_request(request2)
    
    # 示例3: 一般问答
    print("\n🔹 测试3: 一般问答")
    request3 = create_test_request("你们的利率是多少？")
    send_request(request3)

if __name__ == "__main__":
    main()