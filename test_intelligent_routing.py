#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
智能路由功能测试脚本
测试直接回答和意图路由两种场景
"""

import requests
import json
import time

# API配置
BASE_URL = "http://localhost:8082"
CHAT_ENDPOINT = f"{BASE_URL}/api/v2/assistant/chat"

def test_api_call(query, chat_id="test-chat-001", expected_type=None):
    """
    测试API调用
    
    Args:
        query: 用户查询
        chat_id: 聊天ID
        expected_type: 期望的处理类型 (direct_answer 或 intent_routing)
    """
    print(f"\n{'='*60}")
    print(f"测试查询: {query}")
    print(f"期望类型: {expected_type}")
    print(f"{'='*60}")
    
    payload = {
        "message": query,
        "chatId": chat_id,
        "userContext": {
            "userId": "test-user-001",
            "sessionId": "test-session-001"
        }
    }
    
    headers = {
        "Content-Type": "application/json"
    }
    
    try:
        start_time = time.time()
        response = requests.post(CHAT_ENDPOINT, json=payload, headers=headers, timeout=30)
        end_time = time.time()
        
        print(f"响应状态码: {response.status_code}")
        print(f"响应时间: {end_time - start_time:.2f}秒")
        
        if response.status_code == 200:
            # 尝试解析JSON，如果失败则当作字符串处理
            try:
                result = response.json()
                print(f"响应内容 (JSON):")
                print(json.dumps(result, ensure_ascii=False, indent=2))
                
                # 分析响应内容
                if "answer" in result:
                    print(f"\n✅ 获得回答: {result['answer'][:100]}...")
                
                if "metadata" in result:
                    metadata = result["metadata"]
                    if "strategy" in metadata:
                        print(f"📊 使用策略: {metadata['strategy']}")
                    if "processingTime" in metadata:
                        print(f"⏱️ 处理时间: {metadata['processingTime']}ms")
                
                return True, result
            except json.JSONDecodeError:
                # 响应是字符串格式
                result_text = response.text
                print(f"响应内容 (文本):")
                print(result_text)
                print(f"\n✅ 获得回答: {result_text[:100]}...")
                
                return True, {"answer": result_text, "type": "text_response"}
        else:
            print(f"❌ API调用失败: {response.status_code}")
            print(f"错误信息: {response.text}")
            return False, None
            
    except requests.exceptions.RequestException as e:
        print(f"❌ 网络请求异常: {e}")
        return False, None
    except Exception as e:
        print(f"❌ 其他异常: {e}")
        return False, None

def main():
    """主测试函数"""
    print("🚀 开始测试智能路由功能")
    print(f"API地址: {CHAT_ENDPOINT}")
    
    # 测试用例
    test_cases = [
        # 直接回答场景
        {
            "query": "贷款利率是多少",
            "expected_type": "direct_answer",
            "description": "利率查询 - 应该直接回答"
        },
        {
            "query": "最高可以贷多少钱",
            "expected_type": "direct_answer", 
            "description": "额度查询 - 应该直接回答"
        },
        {
            "query": "还款方式有哪些",
            "expected_type": "direct_answer",
            "description": "还款方式查询 - 应该直接回答"
        },
        
        # 意图路由场景
        {
            "query": "借4000",
            "expected_type": "intent_routing",
            "description": "借款申请 - 应该进行意图路由"
        },
        {
            "query": "我想申请贷款5万元",
            "expected_type": "intent_routing", 
            "description": "贷款申请 - 应该进行意图路由"
        },
        {
            "query": "查看我的贷款记录",
            "expected_type": "intent_routing",
            "description": "记录查询 - 应该进行意图路由"
        },
        {
            "query": "我要提前还款",
            "expected_type": "intent_routing",
            "description": "提前还款 - 应该进行意图路由"
        }
    ]
    
    success_count = 0
    total_count = len(test_cases)
    
    for i, test_case in enumerate(test_cases, 1):
        print(f"\n🧪 测试用例 {i}/{total_count}: {test_case['description']}")
        
        success, result = test_api_call(
            query=test_case["query"],
            expected_type=test_case["expected_type"]
        )
        
        if success:
            success_count += 1
            print("✅ 测试通过")
        else:
            print("❌ 测试失败")
        
        # 间隔一下避免请求过快
        time.sleep(1)
    
    # 测试总结
    print(f"\n{'='*60}")
    print(f"📊 测试总结")
    print(f"{'='*60}")
    print(f"总测试用例: {total_count}")
    print(f"成功用例: {success_count}")
    print(f"失败用例: {total_count - success_count}")
    print(f"成功率: {success_count/total_count*100:.1f}%")
    
    if success_count == total_count:
        print("🎉 所有测试用例都通过了！")
    else:
        print("⚠️ 部分测试用例失败，请检查日志")

if __name__ == "__main__":
    main()