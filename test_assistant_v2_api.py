#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
测试CustomerSupportAssistantV2接口的脚本
包含多种测试场景的请求数据构造
"""

import requests
import json
import uuid
from datetime import datetime

# 服务配置
BASE_URL = "http://localhost:8080"
API_ENDPOINT = f"{BASE_URL}/api/v2/assistant/chat"
HEALTH_ENDPOINT = f"{BASE_URL}/api/v2/assistant/health"

class AssistantV2Tester:
    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        })
    
    def generate_chat_id(self):
        """生成唯一的聊天ID"""
        return f"test_chat_{uuid.uuid4().hex[:8]}_{int(datetime.now().timestamp())}"
    
    def test_health(self):
        """测试健康检查接口"""
        print("🔍 测试健康检查接口...")
        try:
            response = self.session.get(HEALTH_ENDPOINT)
            print(f"状态码: {response.status_code}")
            print(f"响应: {response.text}")
            return response.status_code == 200
        except Exception as e:
            print(f"❌ 健康检查失败: {e}")
            return False
    
    def create_basic_request(self, user_message, chat_id=None):
        """创建基础请求数据"""
        if chat_id is None:
            chat_id = self.generate_chat_id()
        
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
    
    def create_loan_generation_request(self, chat_id=None):
        """创建借款方案生成请求"""
        request = self.create_basic_request("我想借5万元，分12期，用于装修", chat_id)
        return request
    
    def create_loan_modification_request(self, chat_id=None):
        """创建借款方案修改请求"""
        request = self.create_basic_request("我想把借款金额改成3万元", chat_id)
        return request
    
    def create_clarification_request(self, chat_id=None):
        """创建需要澄清的请求"""
        request = self.create_basic_request("我想借钱", chat_id)
        return request
    
    def create_general_qa_request(self, chat_id=None):
        """创建一般问答请求"""
        request = self.create_basic_request("你们的利率是多少？", chat_id)
        return request
    
    def create_unauthorized_request(self, chat_id=None):
        """创建未授信用户请求"""
        request = self.create_basic_request("我想借5万元", chat_id)
        request["authorized"] = False
        return request
    
    def send_request(self, request_data, description=""):
        """发送请求并处理响应"""
        print(f"\n📤 发送请求: {description}")
        print("=" * 60)
        print("请求数据:")
        print(json.dumps(request_data, ensure_ascii=False, indent=2))
        print("-" * 60)
        
        try:
            response = self.session.post(API_ENDPOINT, json=request_data)
            print(f"状态码: {response.status_code}")
            print("响应内容:")
            print(response.text)
            
            if response.status_code == 200:
                print("✅ 请求成功")
            else:
                print("❌ 请求失败")
            
            return response
        except Exception as e:
            print(f"❌ 请求异常: {e}")
            return None
    
    def run_test_scenarios(self):
        """运行所有测试场景"""
        print("🚀 开始测试CustomerSupportAssistantV2接口")
        print("=" * 80)
        
        # 1. 健康检查
        if not self.test_health():
            print("❌ 服务不可用，终止测试")
            return
        
        print("\n" + "=" * 80)
        
        # 2. 借款方案生成测试
        chat_id = self.generate_chat_id()
        loan_request = self.create_loan_generation_request(chat_id)
        self.send_request(loan_request, "借款方案生成 - 完整参数")
        
        # 3. 借款方案修改测试
        modify_request = self.create_loan_modification_request(chat_id)
        self.send_request(modify_request, "借款方案修改 - 同一会话")
        
        # 4. 澄清请求测试
        clarify_request = self.create_clarification_request()
        self.send_request(clarify_request, "澄清请求 - 参数不完整")
        
        # 5. 一般问答测试
        qa_request = self.create_general_qa_request()
        self.send_request(qa_request, "一般问答 - 利率咨询")
        
        # 6. 未授信用户测试
        unauth_request = self.create_unauthorized_request()
        self.send_request(unauth_request, "未授信用户 - 借款请求")
        
        print("\n" + "=" * 80)
        print("🎉 所有测试场景执行完成")
    
    def run_custom_test(self, user_message, **kwargs):
        """运行自定义测试"""
        print(f"\n🔧 自定义测试: {user_message}")
        print("=" * 60)
        
        request = self.create_basic_request(user_message)
        
        # 应用自定义参数
        for key, value in kwargs.items():
            if hasattr(request, key) or key in request:
                request[key] = value
                print(f"自定义参数: {key} = {value}")
        
        return self.send_request(request, "自定义测试")

def main():
    print("🔧 CustomerSupportAssistantV2 API测试工具")
    print("=" * 80)
    
    tester = AssistantV2Tester()
    
    while True:
        print("\n请选择测试模式:")
        print("1. 运行所有测试场景")
        print("2. 借款方案生成测试")
        print("3. 借款方案修改测试")
        print("4. 澄清请求测试")
        print("5. 一般问答测试")
        print("6. 未授信用户测试")
        print("7. 自定义测试")
        print("8. 健康检查")
        print("0. 退出")
        
        choice = input("\n请输入选择 (0-8): ").strip()
        
        if choice == "0":
            print("👋 退出测试工具")
            break
        elif choice == "1":
            tester.run_test_scenarios()
        elif choice == "2":
            request = tester.create_loan_generation_request()
            tester.send_request(request, "借款方案生成测试")
        elif choice == "3":
            request = tester.create_loan_modification_request()
            tester.send_request(request, "借款方案修改测试")
        elif choice == "4":
            request = tester.create_clarification_request()
            tester.send_request(request, "澄清请求测试")
        elif choice == "5":
            request = tester.create_general_qa_request()
            tester.send_request(request, "一般问答测试")
        elif choice == "6":
            request = tester.create_unauthorized_request()
            tester.send_request(request, "未授信用户测试")
        elif choice == "7":
            user_message = input("请输入用户消息: ").strip()
            if user_message:
                print("\n可选参数设置 (直接回车跳过):")
                authorized = input("授信状态 (true/false): ").strip()
                available_credit = input("可用额度: ").strip()
                
                kwargs = {}
                if authorized:
                    kwargs["authorized"] = authorized.lower() == "true"
                if available_credit:
                    try:
                        kwargs["availableCredit"] = float(available_credit)
                    except ValueError:
                        print("⚠️ 额度格式错误，使用默认值")
                
                tester.run_custom_test(user_message, **kwargs)
        elif choice == "8":
            tester.test_health()
        else:
            print("❌ 无效选择，请重新输入")

if __name__ == "__main__":
    main()