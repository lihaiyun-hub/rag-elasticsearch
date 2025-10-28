#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
重建索引以使用BGE模型的脚本
"""

import requests
import json

class IndexRebuilder:
    def __init__(self, es_host="http://127.0.0.1:9200", username="elastic", password="yingzi"):
        self.es_host = es_host
        self.auth = (username, password)
        self.index_name = "customer_support_qa"
    
    def check_current_index(self):
        """检查当前索引状态"""
        try:
            response = requests.get(
                f"{self.es_host}/{self.index_name}/_mapping",
                auth=self.auth
            )
            
            if response.status_code == 200:
                mapping = response.json()
                index_mapping = mapping.get(self.index_name, {})
                properties = index_mapping.get("mappings", {}).get("properties", {})
                embedding_field = properties.get("embedding", {})
                
                if embedding_field:
                    dims = embedding_field.get("dims", "未知")
                    similarity = embedding_field.get("similarity", "未知")
                    print(f"当前索引 '{self.index_name}' 状态:")
                    print(f"  向量维度: {dims}")
                    print(f"  相似度算法: {similarity}")
                    return dims
                else:
                    print(f"索引 '{self.index_name}' 中没有找到embedding字段")
                    return None
            elif response.status_code == 404:
                print(f"索引 '{self.index_name}' 不存在")
                return None
            else:
                print(f"检查索引失败: {response.status_code}")
                return None
                
        except Exception as e:
            print(f"检查索引异常: {e}")
            return None
    
    def get_document_count(self):
        """获取文档数量"""
        try:
            response = requests.get(
                f"{self.es_host}/{self.index_name}/_count",
                auth=self.auth
            )
            
            if response.status_code == 200:
                result = response.json()
                count = result.get("count", 0)
                print(f"当前索引文档数量: {count}")
                return count
            else:
                print(f"获取文档数量失败: {response.status_code}")
                return 0
                
        except Exception as e:
            print(f"获取文档数量异常: {e}")
            return 0
    
    def backup_data(self):
        """备份现有数据"""
        try:
            response = requests.post(
                f"{self.es_host}/{self.index_name}/_search",
                headers={"Content-Type": "application/json"},
                data=json.dumps({
                    "size": 10000,  # 假设不超过10000条记录
                    "query": {"match_all": {}}
                }),
                auth=self.auth
            )
            
            if response.status_code == 200:
                result = response.json()
                hits = result.get("hits", {}).get("hits", [])
                
                # 保存到文件
                backup_data = []
                for hit in hits:
                    doc = hit["_source"]
                    # 移除embedding字段，因为我们要用新模型重新生成
                    if "embedding" in doc:
                        del doc["embedding"]
                    backup_data.append(doc)
                
                with open("index_backup.json", "w", encoding="utf-8") as f:
                    json.dump(backup_data, f, ensure_ascii=False, indent=2)
                
                print(f"已备份 {len(backup_data)} 条记录到 index_backup.json")
                return backup_data
            else:
                print(f"备份数据失败: {response.status_code}")
                return []
                
        except Exception as e:
            print(f"备份数据异常: {e}")
            return []
    
    def delete_index(self):
        """删除现有索引"""
        try:
            response = requests.delete(
                f"{self.es_host}/{self.index_name}",
                auth=self.auth
            )
            
            if response.status_code == 200:
                print(f"成功删除索引 '{self.index_name}'")
                return True
            elif response.status_code == 404:
                print(f"索引 '{self.index_name}' 不存在，无需删除")
                return True
            else:
                print(f"删除索引失败: {response.status_code}")
                print(f"响应内容: {response.text}")
                return False
                
        except Exception as e:
            print(f"删除索引异常: {e}")
            return False
    
    def wait_for_spring_ai_recreation(self):
        """等待Spring AI重新创建索引"""
        import time
        
        print("等待Spring AI重新创建索引...")
        print("请重启Spring Boot应用程序以应用新的BGE模型配置")
        print("重启后，Spring AI会自动创建1024维的新索引")
        
        # 检查索引是否重新创建
        for i in range(30):  # 等待最多30秒
            time.sleep(1)
            try:
                response = requests.get(
                    f"{self.es_host}/{self.index_name}/_mapping",
                    auth=self.auth
                )
                
                if response.status_code == 200:
                    mapping = response.json()
                    index_mapping = mapping.get(self.index_name, {})
                    properties = index_mapping.get("mappings", {}).get("properties", {})
                    embedding_field = properties.get("embedding", {})
                    
                    if embedding_field:
                        dims = embedding_field.get("dims", "未知")
                        print(f"检测到新索引已创建，向量维度: {dims}")
                        return dims == 1024
                        
            except:
                pass
            
            if i % 5 == 0:
                print(f"等待中... ({i+1}/30秒)")
        
        print("等待超时，请手动检查索引状态")
        return False

def main():
    """主函数"""
    rebuilder = IndexRebuilder()
    
    print("=== 索引重建工具 ===")
    print("此工具将帮助您从OpenAI模型(1536维)切换到BGE模型(1024维)")
    
    # 检查当前索引
    current_dims = rebuilder.check_current_index()
    if current_dims is None:
        print("无法检查当前索引状态")
        return
    
    if current_dims == 1024:
        print("当前索引已经是1024维，无需重建")
        return
    
    # 获取文档数量
    doc_count = rebuilder.get_document_count()
    
    print(f"\n当前索引使用 {current_dims} 维向量，需要重建为1024维")
    print(f"索引中有 {doc_count} 条文档")
    
    # 确认操作
    confirm = input("\n是否继续重建索引？这将删除现有索引。(y/N): ")
    if confirm.lower() != 'y':
        print("操作已取消")
        return
    
    # 备份数据
    print("\n正在备份现有数据...")
    backup_data = rebuilder.backup_data()
    if not backup_data:
        print("备份失败，操作已取消")
        return
    
    # 删除索引
    print("\n正在删除现有索引...")
    if not rebuilder.delete_index():
        print("删除索引失败，操作已取消")
        return
    
    print("\n索引已删除。现在需要重启Spring Boot应用程序。")
    print("重启后，Spring AI会使用新的BGE模型配置自动创建1024维的索引。")
    print("\n备份文件: index_backup.json")
    print("如果需要恢复数据，请重启应用程序后运行数据导入脚本。")

if __name__ == "__main__":
    main()