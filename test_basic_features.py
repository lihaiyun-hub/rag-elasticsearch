#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
基础功能测试脚本 - 不依赖AI模型
只测试知识库检索和基础问答功能
"""

import requests
import json
import uuid
from datetime import datetime

# 配置
API_URL = "http://localhost:8080/api/v2/assistant/chat"

def generate_chat_id():
    """生成唯一的聊天ID"""
    return f"test_{uuid.uuid4().hex[:8]}"

def create_basic_request(user_message, chat_id=None):
    """创建基础请求数据"""
    if chat_id is None:
        chat_id = generate_chat_id()
    
    return {
        "chatId": chat_id,
        "userMessage": user_message,
        "userName": "测试用户",
        "availableCredit": 50000.0,
        "recentRepaymentStatus": "正常",
        "authorized": True,
        "termOptions": [3, 6, 9, 12],
        "loanPurposes": ["消费", "装修", "教育", "旅游"],
        "bankCardNumber": "1234",
        "bankName": "工商银行"
    }

def send_request(request_data):
    """发送请求并返回响应"""
    try:
        print(f"📤 发送请求到: {API_URL}")
        print(f"📋 请求数据:")
        print(json.dumps(request_data, ensure_ascii=False, indent=2))
        print("-" * 50)
        
        response = requests.post(
            API_URL,
            json=request_data,
            headers={"Content-Type": "application/json"},
            timeout=30
        )
        
        print(f"📊 状态码: {response.status_code}")
        print(f"📄 响应内容:")
        print(response.text)
        print("=" * 50)
        
        return response
        
    except requests.exceptions.ConnectionError:
        print("❌ 连接失败: 请确保服务器正在运行")
        return None
    except requests.exceptions.Timeout:
        print("❌ 请求超时")
        return None
    except Exception as e:
        print(f"❌ 请求失败: {e}")
        return None

def test_health_check():
    """测试健康检查"""
    try:
        health_url = "http://localhost:8080/actuator/health"
        response = requests.get(health_url, timeout=5)
        print(f"🏥 健康检查: {response.status_code}")
        if response.status_code == 200:
            print("✅ 服务器运行正常")
            return True
        else:
            print("❌ 服务器状态异常")
            return False
    except:
        print("❌ 无法连接到服务器")
        return False

def main():
    """主测试函数"""
    print("🚀 开始基础功能测试")
    print("=" * 50)
    
    # 健康检查
    if not test_health_check():
        print("请先启动服务器")
        return
    
    print()
    
    # 测试用例 - 只测试基础知识库查询
    test_cases = [
        {
            "name": "利率查询",
            "message": "你们的利率是多少？"
        },
        {
            "name": "贷款条件查询", 
            "message": "申请贷款需要什么条件？"
        },
        {
            "name": "还款方式查询",
            "message": "有哪些还款方式？"
        },
        {
            "name": "额度查询",
            "message": "最高可以贷多少钱？"
        },
        {
            "name": "基础问候",
            "message": "你好"
        }
    ]
    
    print(f"📋 共有 {len(test_cases)} 个测试用例")
    print()
    
    success_count = 0
    
    for i, test_case in enumerate(test_cases, 1):
        print(f"🔹 测试{i}: {test_case['name']}")
        
        request_data = create_basic_request(test_case['message'])
        response = send_request(request_data)
        
        if response and response.status_code == 200:
            # 检查响应内容
            response_text = response.text.strip()
            if response_text and "系统处理您的请求时出现问题" not in response_text:
                print("✅ 测试通过")
                success_count += 1
            else:
                print("❌ 测试失败: 系统返回错误信息")
        else:
            print("❌ 测试失败: 请求失败")
        
        print()
    
    # 测试总结
    print("=" * 50)
    print(f"📊 测试总结:")
    print(f"   总测试数: {len(test_cases)}")
    print(f"   成功数: {success_count}")
    print(f"   失败数: {len(test_cases) - success_count}")
    print(f"   成功率: {success_count/len(test_cases)*100:.1f}%")
    
    if success_count == len(test_cases):
        print("🎉 所有测试通过！")
    elif success_count > 0:
        print("⚠️ 部分测试通过，请检查失败的测试用例")
    else:
        print("❌ 所有测试失败，请检查服务器配置")

if __name__ == "__main__":
    main()