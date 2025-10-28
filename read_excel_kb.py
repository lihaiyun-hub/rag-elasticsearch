#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
读取Excel知识库文件
"""

import pandas as pd
import os

def read_excel_knowledge_base():
    """读取Excel知识库文件"""
    
    excel_path = r"d:\workspace\rag-elasticsearch\src\main\resources\rag\问答知识库.xlsx"
    
    if not os.path.exists(excel_path):
        print(f"❌ Excel文件不存在: {excel_path}")
        return
    
    try:
        # 读取Excel文件
        df = pd.read_excel(excel_path)
        
        print(f"📊 Excel文件读取成功，共 {len(df)} 行数据")
        print(f"📋 列名: {list(df.columns)}")
        print("=" * 80)
        
        # 显示前几行数据
        print("📝 前5行数据:")
        print(df.head().to_string())
        print("=" * 80)
        
        # 搜索包含模板变量的行
        template_vars = ["${", "maxPrice", "available_credit", "max_loan_amount"]
        
        print("🔍 搜索包含模板变量的行:")
        found_template_vars = False
        
        for index, row in df.iterrows():
            row_text = str(row.to_dict())
            
            for var in template_vars:
                if var in row_text:
                    print(f"\n行 {index + 1} 包含 '{var}':")
                    print(f"数据: {row.to_dict()}")
                    found_template_vars = True
                    break
        
        if not found_template_vars:
            print("❌ 未找到包含模板变量的行")
        
        # 搜索包含特定文本的行
        search_texts = ["最大可借额度", "根据系统查询", "贷款额度"]
        
        print("\n🔍 搜索包含特定文本的行:")
        found_texts = False
        
        for search_text in search_texts:
            print(f"\n搜索: '{search_text}'")
            
            for index, row in df.iterrows():
                row_text = str(row.to_dict())
                
                if search_text in row_text:
                    print(f"行 {index + 1}: {row.to_dict()}")
                    found_texts = True
        
        if not found_texts:
            print("❌ 未找到包含特定文本的行")
            
    except Exception as e:
        print(f"❌ 读取Excel文件出错: {e}")

if __name__ == "__main__":
    print("📖 读取Excel知识库文件")
    read_excel_knowledge_base()