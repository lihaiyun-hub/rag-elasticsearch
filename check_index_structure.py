#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
检查Elasticsearch索引结构和向量化字段的脚本
"""

import requests
import json

class IndexStructureChecker:
    def __init__(self, es_host="http://127.0.0.1:9200", username="elastic", password="yingzi"):
        self.es_host = es_host
        self.auth = (username, password)
        self.index_name = "customer_support_qa"
    
    def get_index_mapping(self):
        """获取索引映射结构"""
        try:
            response = requests.get(
                f"{self.es_host}/{self.index_name}/_mapping",
                auth=self.auth
            )
            
            if response.status_code == 200:
                mapping = response.json()
                return mapping
            else:
                print(f"获取索引映射失败: {response.status_code}")
                print(f"响应内容: {response.text}")
                return None
                
        except Exception as e:
            print(f"获取索引映射异常: {e}")
            return None
    
    def get_index_settings(self):
        """获取索引设置"""
        try:
            response = requests.get(
                f"{self.es_host}/{self.index_name}/_settings",
                auth=self.auth
            )
            
            if response.status_code == 200:
                settings = response.json()
                return settings
            else:
                print(f"获取索引设置失败: {response.status_code}")
                return None
                
        except Exception as e:
            print(f"获取索引设置异常: {e}")
            return None
    
    def analyze_field_types(self, mapping):
        """分析字段类型"""
        if not mapping:
            return
        
        index_mapping = mapping.get(self.index_name, {})
        mappings = index_mapping.get("mappings", {})
        properties = mappings.get("properties", {})
        
        print("=== 索引字段结构分析 ===")
        print(f"索引名称: {self.index_name}")
        print(f"字段总数: {len(properties)}")
        
        vector_fields = []
        text_fields = []
        keyword_fields = []
        date_fields = []
        other_fields = []
        
        for field_name, field_config in properties.items():
            field_type = field_config.get("type", "unknown")
            
            print(f"\n字段: {field_name}")
            print(f"  类型: {field_type}")
            
            if field_type == "dense_vector":
                vector_fields.append(field_name)
                dims = field_config.get("dims", "未知")
                similarity = field_config.get("similarity", "未知")
                print(f"  向量维度: {dims}")
                print(f"  相似度算法: {similarity}")
                
            elif field_type == "text":
                text_fields.append(field_name)
                analyzer = field_config.get("analyzer", "未设置")
                print(f"  分析器: {analyzer}")
                
                # 检查是否有fields子字段
                fields = field_config.get("fields", {})
                if fields:
                    print(f"  子字段:")
                    for sub_field, sub_config in fields.items():
                        sub_type = sub_config.get("type", "unknown")
                        print(f"    {sub_field}: {sub_type}")
                        
            elif field_type == "keyword":
                keyword_fields.append(field_name)
                
            elif field_type == "date":
                date_fields.append(field_name)
                date_format = field_config.get("format", "未设置")
                print(f"  日期格式: {date_format}")
                
            else:
                other_fields.append((field_name, field_type))
        
        # 总结
        print(f"\n=== 字段类型总结 ===")
        print(f"向量字段 ({len(vector_fields)}): {vector_fields}")
        print(f"文本字段 ({len(text_fields)}): {text_fields}")
        print(f"关键词字段 ({len(keyword_fields)}): {keyword_fields}")
        print(f"日期字段 ({len(date_fields)}): {date_fields}")
        if other_fields:
            print(f"其他字段 ({len(other_fields)}): {[f'{name}({type_})' for name, type_ in other_fields]}")
        
        return {
            "vector_fields": vector_fields,
            "text_fields": text_fields,
            "keyword_fields": keyword_fields,
            "date_fields": date_fields,
            "other_fields": other_fields
        }
    
    def get_sample_document(self):
        """获取样本文档来查看实际数据结构"""
        try:
            response = requests.post(
                f"{self.es_host}/{self.index_name}/_search",
                headers={"Content-Type": "application/json"},
                data=json.dumps({
                    "size": 1,
                    "query": {"match_all": {}}
                }),
                auth=self.auth
            )
            
            if response.status_code == 200:
                result = response.json()
                hits = result.get("hits", {}).get("hits", [])
                
                if hits:
                    sample_doc = hits[0]["_source"]
                    print(f"\n=== 样本文档结构 ===")
                    for field, value in sample_doc.items():
                        value_type = type(value).__name__
                        if isinstance(value, list) and value:
                            if isinstance(value[0], (int, float)):
                                print(f"{field}: {value_type} (向量数据，长度: {len(value)})")
                            else:
                                print(f"{field}: {value_type} (列表数据)")
                        elif isinstance(value, str):
                            preview = value[:100] + "..." if len(value) > 100 else value
                            print(f"{field}: {value_type} - \"{preview}\"")
                        else:
                            print(f"{field}: {value_type} - {value}")
                    
                    return sample_doc
                else:
                    print("索引中没有文档")
                    return None
            else:
                print(f"获取样本文档失败: {response.status_code}")
                return None
                
        except Exception as e:
            print(f"获取样本文档异常: {e}")
            return None

def main():
    """主函数"""
    checker = IndexStructureChecker()
    
    print("正在检查Elasticsearch索引结构...")
    
    # 获取索引映射
    mapping = checker.get_index_mapping()
    if mapping:
        # 分析字段类型
        field_analysis = checker.analyze_field_types(mapping)
        
        # 获取样本文档
        sample_doc = checker.get_sample_document()
        
        # 获取索引设置
        settings = checker.get_index_settings()
        if settings:
            index_settings = settings.get(checker.index_name, {}).get("settings", {})
            index_info = index_settings.get("index", {})
            
            print(f"\n=== 索引基本信息 ===")
            print(f"创建时间: {index_info.get('creation_date', '未知')}")
            print(f"UUID: {index_info.get('uuid', '未知')}")
            print(f"分片数: {index_info.get('number_of_shards', '未知')}")
            print(f"副本数: {index_info.get('number_of_replicas', '未知')}")
    
    else:
        print("无法获取索引映射信息")

if __name__ == "__main__":
    main()