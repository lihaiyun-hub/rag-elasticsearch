#!/usr/bin/env python3
"""
测试DocumentParserUtils工具类的功能
验证优化后的parseDocumentToKnowledgeRecord方法是否正常工作
"""

import requests
import json
import time

def test_basic_functionality():
    """测试基础功能"""
    base_url = "http://localhost:8080"
    
    print("🔍 测试DocumentParserUtils优化后的功能...")
    print("=" * 60)
    
    # 1. 健康检查
    try:
        response = requests.get(f"{base_url}/actuator/health", timeout=5)
        if response.status_code == 200:
            print("✅ 应用健康检查通过")
        else:
            print(f"❌ 应用健康检查失败: {response.status_code}")
            return False
    except Exception as e:
        print(f"❌ 无法连接到应用: {e}")
        return False
    
    # 2. 测试知识库检索功能（这会使用parseDocumentToKnowledgeRecord方法）
    test_cases = [
        {
            "name": "利率查询",
            "query": "利率是多少",
            "expected_keywords": ["利率", "3.85", "24%"]
        },
        {
            "name": "额度查询", 
            "query": "最高能借多少钱",
            "expected_keywords": ["额度", "50万", "信用"]
        },
        {
            "name": "还款方式查询",
            "query": "还款方式有哪些",
            "expected_keywords": ["还款方式", "等额本息", "等额本金"]
        }
    ]
    
    success_count = 0
    total_count = len(test_cases)
    
    for test_case in test_cases:
        print(f"\n🧪 测试: {test_case['name']}")
        print(f"   查询: {test_case['query']}")
        
        try:
            # 发送请求到一般问答接口
            response = requests.post(
                f"{base_url}/api/chat/general-qa",
                json={
                    "message": test_case['query'],
                    "chatId": f"test_{int(time.time())}",
                    "userId": "test_user"
                },
                headers={"Content-Type": "application/json"},
                timeout=10
            )
            
            if response.status_code == 200:
                result = response.json()
                response_text = result.get('response', '')
                
                # 检查是否包含期望的关键词
                keywords_found = []
                for keyword in test_case['expected_keywords']:
                    if keyword in response_text:
                        keywords_found.append(keyword)
                
                if keywords_found:
                    print(f"   ✅ 成功 - 找到关键词: {keywords_found}")
                    print(f"   📝 响应: {response_text[:100]}...")
                    success_count += 1
                else:
                    print(f"   ⚠️  部分成功 - 响应: {response_text[:100]}...")
                    if "系统暂时无法处理" not in response_text:
                        success_count += 0.5  # 部分成功
            else:
                print(f"   ❌ 请求失败: {response.status_code}")
                print(f"   📝 响应: {response.text[:100]}...")
                
        except Exception as e:
            print(f"   ❌ 请求异常: {e}")
    
    print("\n" + "=" * 60)
    print(f"📊 测试结果: {success_count}/{total_count} 成功")
    success_rate = (success_count / total_count) * 100
    print(f"📈 成功率: {success_rate:.1f}%")
    
    if success_rate >= 60:
        print("🎉 DocumentParserUtils优化验证成功！")
        print("✨ parseDocumentToKnowledgeRecord方法工作正常")
        return True
    else:
        print("⚠️  需要进一步调试")
        return False

if __name__ == "__main__":
    print("🚀 开始测试DocumentParserUtils优化...")
    test_basic_functionality()