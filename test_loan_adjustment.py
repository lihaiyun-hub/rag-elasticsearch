#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
借款调整需求测试脚本
测试系统对各种借款调整请求的处理能力
"""

import requests
import json
import time

# API配置
BASE_URL = "http://localhost:8080"
API_ENDPOINT = f"{BASE_URL}/api/v2/assistant/chat"

def test_loan_adjustment_scenarios():
    """测试借款调整场景"""
    
    # 测试用例：各种借款调整需求
    test_cases = [
        {
            "name": "调整借款需求",
            "message": "调整借款需求",
            "expected": "应该询问具体要调整什么"
        },
        {
            "name": "需要调整需求",
            "message": "需要调整需求",
            "expected": "应该询问调整的具体内容"
        },
        {
            "name": "提高借款金额",
            "message": "提高借款金额",
            "expected": "应该询问要提高到多少金额"
        },
        {
            "name": "想调整借款金额",
            "message": "想调整借款金额",
            "expected": "应该询问新的借款金额"
        },
        {
            "name": "调整金额",
            "message": "调整金额",
            "expected": "应该询问调整到多少金额"
        },
        {
            "name": "可以调整额度吗",
            "message": "可以调整额度吗",
            "expected": "应该确认可以调整并询问新额度"
        },
        {
            "name": "调整借款额度",
            "message": "调整借款额度",
            "expected": "应该询问新的借款额度"
        },
        {
            "name": "调高一下",
            "message": "调高一下",
            "expected": "应该询问调高到多少"
        }
    ]
    
    print("开始测试借款调整需求...")
    print("="*60)
    
    for i, test_case in enumerate(test_cases, 1):
        print(f"\n测试用例 {i}: {test_case['name']}")
        print(f"用户消息: {test_case['message']}")
        print(f"期望结果: {test_case['expected']}")
        print("-" * 40)
        
        # 构建请求数据
        request_data = {
            "chatId": f"test-adjustment-{i}",
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
                
                # 分析响应是否包含调整相关的询问
                adjustment_keywords = [
                    "调整", "修改", "变更", "多少", "金额", "额度", 
                    "请问", "您想", "需要", "具体", "新的", "提高到"
                ]
                
                contains_adjustment_response = any(keyword in response_text for keyword in adjustment_keywords)
                
                if contains_adjustment_response:
                    print("✅ 检测到调整相关响应")
                else:
                    print("❌ 未检测到调整相关响应")
                    
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

def test_modify_plan_scenario():
    """测试修改方案的具体场景"""
    print("\n\n测试修改方案场景...")
    print("="*60)
    
    # 模拟用户已有借款方案，想要调整
    test_case = {
        "chatId": "test-modify-plan",
        "userMessage": "我想把借款金额从5000调整到8000",
        "userName": "测试用户",
        "availableCredit": 50000.0,
        "recentRepaymentStatus": "正常",
        "authorized": True
    }
    
    print(f"测试场景: 具体的金额调整请求")
    print(f"用户消息: {test_case['userMessage']}")
    print("-" * 40)
    
    try:
        response = requests.post(
            API_ENDPOINT,
            json=test_case,
            headers={"Content-Type": "application/json"},
            timeout=30
        )
        
        print(f"响应状态码: {response.status_code}")
        print(f"响应内容: {response.text}")
        
    except Exception as e:
        print(f"❌ 请求异常: {e}")

if __name__ == "__main__":
    print("借款调整需求测试脚本")
    print("测试目标: 验证系统对各种借款调整请求的理解和处理能力")
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
    test_loan_adjustment_scenarios()
    test_modify_plan_scenario()
    
    print("\n测试完成！")
    print("\n建议的改进方向:")
    print("1. 系统应该能识别调整类型（金额、期数、用途）")
    print("2. 对于模糊的调整请求，应该主动询问具体要调整什么")
    print("3. 对于具体的调整请求，应该确认新的参数值")
    print("4. 提供友好的调整确认和重新生成方案的流程")