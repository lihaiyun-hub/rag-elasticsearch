#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
澄清功能测试脚本
通过模拟用户借款请求来测试澄清逻辑
"""

import requests
import json
import time

# API配置
BASE_URL = "http://localhost:8080"
API_ENDPOINT = f"{BASE_URL}/api/v2/assistant/chat"

def test_clarification_scenarios():
    """测试各种澄清场景"""
    
    # 测试用例：模拟需要澄清的用户请求
    test_cases = [
        {
            "name": "缺少金额信息",
            "message": "我想借钱分6期",
            "expected": "应该询问借款金额"
        },
        {
            "name": "缺少期数信息", 
            "message": "我想借5000元",
            "expected": "应该询问分期期数"
        },
        {
            "name": "缺少用途信息",
            "message": "我想借5000元分6期",
            "expected": "应该询问借款用途"
        },
        {
            "name": "信息不完整",
            "message": "我要借钱",
            "expected": "应该询问金额、期数和用途"
        },
        {
            "name": "模糊的借款请求",
            "message": "我需要资金周转",
            "expected": "应该询问具体信息"
        }
    ]
    
    print("开始测试澄清功能...")
    print("="*60)
    
    for i, test_case in enumerate(test_cases, 1):
        print(f"\n测试用例 {i}: {test_case['name']}")
        print(f"用户消息: {test_case['message']}")
        print(f"期望结果: {test_case['expected']}")
        print("-" * 40)
        
        # 构建请求数据
        request_data = {
            "chatId": f"test-clarification-{i}",
            "userMessage": test_case['message'],
            "userName": "测试用户",
            "availableCredit": 50000.0,
            "recentRepaymentStatus": "正常",
            "authorized": True
        }
        
        try:
            # 发送请求
            start_time = time.time()
            response = requests.post(
                API_ENDPOINT,
                json=request_data,
                headers={"Content-Type": "application/json"},
                timeout=30
            )
            end_time = time.time()
            
            print(f"响应状态码: {response.status_code}")
            print(f"响应时间: {end_time - start_time:.2f}秒")
            
            if response.status_code == 200:
                response_text = response.text
                print(f"响应内容: {response_text}")
                
                # 分析响应是否包含澄清相关内容
                clarification_keywords = [
                    "请问", "您想", "需要", "多少", "几期", "用途", 
                    "金额", "期数", "借款用途", "分期", "具体"
                ]
                
                contains_clarification = any(keyword in response_text for keyword in clarification_keywords)
                
                if contains_clarification:
                    print("✅ 检测到澄清响应")
                else:
                    print("❌ 未检测到澄清响应")
                    
            else:
                print(f"❌ 请求失败: {response.status_code}")
                print(f"错误信息: {response.text}")
                
        except requests.exceptions.RequestException as e:
            print(f"❌ 请求异常: {e}")
        except Exception as e:
            print(f"❌ 其他异常: {e}")
            
        print("\n" + "="*60)
        
        # 添加延迟避免请求过快
        time.sleep(1)

def test_direct_json_clarification():
    """测试直接的JSON澄清处理（如果有直接端点的话）"""
    print("\n\n测试直接JSON澄清处理...")
    print("="*60)
    
    # 模拟意图提取服务返回的澄清JSON
    clarification_json = {
        "operation": "clarification",
        "parameters": {
            "amount": "true",
            "term": "",
            "purpose": ""
        }
    }
    
    print(f"模拟澄清JSON: {json.dumps(clarification_json, ensure_ascii=False, indent=2)}")
    
    # 注意：这里我们无法直接测试routeByJsonOperation方法，
    # 因为它是私有方法，只能通过完整的聊天流程来触发

if __name__ == "__main__":
    print("澄清功能测试脚本")
    print("测试目标: 验证系统能够正确识别并处理需要澄清的用户请求")
    print("="*60)
    
    # 检查服务是否可用
    try:
        health_response = requests.get(f"{BASE_URL}/api/v2/assistant/health", timeout=5)
        if health_response.status_code == 200:
            print("✅ 服务健康检查通过")
        else:
            print(f"❌ 服务健康检查失败: {health_response.status_code}")
            exit(1)
    except Exception as e:
        print(f"❌ 无法连接到服务: {e}")
        exit(1)
    
    # 执行测试
    test_clarification_scenarios()
    test_direct_json_clarification()
    
    print("\n测试完成！")