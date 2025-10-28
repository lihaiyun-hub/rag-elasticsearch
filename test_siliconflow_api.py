#!/usr/bin/env python3
"""
测试SiliconFlow embedding API的可访问性
"""
import requests
import json

def test_siliconflow_embedding():
    """测试SiliconFlow embedding API"""
    
    # API配置
    api_key = "sk-vcqgdvzbcchptznjpgcsyrfzrclvuvsidsrdpoyadgzgmtpg"
    base_url = "https://api.siliconflow.cn/v1"
    endpoint = f"{base_url}/embeddings"
    
    # 请求头
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }
    
    # 请求数据
    data = {
        "model": "BAAI/bge-large-zh-v1.5",
        "input": ["测试文本"],
        "encoding_format": "float"
    }
    
    try:
        print(f"正在测试API端点: {endpoint}")
        print(f"使用模型: {data['model']}")
        
        response = requests.post(endpoint, headers=headers, json=data, timeout=30)
        
        print(f"响应状态码: {response.status_code}")
        print(f"响应头: {dict(response.headers)}")
        
        if response.status_code == 200:
            result = response.json()
            print("✅ API调用成功!")
            print(f"返回的embedding维度: {len(result['data'][0]['embedding'])}")
            return True
        else:
            print(f"❌ API调用失败!")
            print(f"错误响应: {response.text}")
            return False
            
    except requests.exceptions.RequestException as e:
        print(f"❌ 请求异常: {e}")
        return False
    except Exception as e:
        print(f"❌ 其他错误: {e}")
        return False

if __name__ == "__main__":
    test_siliconflow_embedding()