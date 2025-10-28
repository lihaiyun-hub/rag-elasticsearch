#!/usr/bin/env python3
"""
删除现有的Elasticsearch索引
为重建索引做准备
"""

from elasticsearch import Elasticsearch
import json

def delete_index():
    """删除现有的customer_support_qa索引"""
    
    # 连接到Elasticsearch
    es = Elasticsearch([{'host': 'localhost', 'port': 9200, 'scheme': 'http'}])
    
    index_name = "customer_support_qa"
    
    try:
        # 检查连接
        if not es.ping():
            print("❌ 无法连接到Elasticsearch")
            return False
            
        print("✅ 成功连接到Elasticsearch")
        
        # 检查索引是否存在
        if es.indices.exists(index=index_name):
            print(f"📋 索引 '{index_name}' 存在，准备删除...")
            
            # 获取删除前的文档数量
            try:
                count_response = es.count(index=index_name)
                doc_count = count_response['count']
                print(f"📊 删除前索引包含 {doc_count} 个文档")
            except Exception as e:
                print(f"⚠️ 无法获取文档数量: {e}")
                doc_count = "未知"
            
            # 删除索引
            delete_response = es.indices.delete(index=index_name)
            
            if delete_response.get('acknowledged', False):
                print(f"✅ 成功删除索引 '{index_name}'")
                print(f"📊 已删除 {doc_count} 个文档")
                return True
            else:
                print(f"❌ 删除索引失败: {delete_response}")
                return False
        else:
            print(f"ℹ️ 索引 '{index_name}' 不存在，无需删除")
            return True
            
    except Exception as e:
        print(f"❌ 删除索引时发生错误: {e}")
        return False

if __name__ == "__main__":
    print("🗑️ 开始删除Elasticsearch索引...")
    success = delete_index()
    
    if success:
        print("\n✅ 索引删除完成！")
        print("💡 现在可以重新启动Spring Boot应用程序来重建索引")
    else:
        print("\n❌ 索引删除失败！")