#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
单个请求测试脚本 - 用于详细调试
"""

import requests
import json
import uuid

def test_single_request():
    """测试单个借款方案生成请求"""
    
    # 构造请求数据
    request_data = {
        "chatId": f"debug_test_{uuid.uuid4().hex[:8]}",
        "userMessage": "我想借5万元，分12期，用于装修",
        "userName": "调试用户",
        "availableCredit": 50000.0,
        "recentRepaymentStatus": "正常",
        "authorized": True,
        "termOptions": [3, 6, 9, 12],
        "loanPurposes": ["消费", "装修", "教育", "旅游"],
        "bankCardNumber": "1234",
        "bankName": "工商银行"
    }
    
    print("🔍 调试测试 - 借款方案生成")
    print("=" * 60)
    print("📋 请求数据:")
    print(json.dumps(request_data, ensure_ascii=False, indent=2))
    print("-" * 60)
    
    try:
        # 发送请求
        response = requests.post(
            "http://localhost:8080/api/v2/assistant/chat",
            json=request_data,
            headers={
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            timeout=30
        )
        
        print(f"📊 状态码: {response.status_code}")
        print(f"📄 响应头: {dict(response.headers)}")
        print("📄 响应内容:")
        print(response.text)
        
        # 尝试解析JSON响应
        try:
            response_json = response.json()
            print("\n📄 格式化JSON响应:")
            print(json.dumps(response_json, ensure_ascii=False, indent=2))
        except:
            print("⚠️ 响应不是有效的JSON格式")
        
        return response
        
    except requests.exceptions.Timeout:
        print("❌ 请求超时")
    except requests.exceptions.ConnectionError:
        print("❌ 连接失败")
    except Exception as e:
        print(f"❌ 请求异常: {e}")
    
    return None

if __name__ == "__main__":
    test_single_request()