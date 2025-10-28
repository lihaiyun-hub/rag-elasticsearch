#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
分析Excel文件结构和内容的脚本
"""

import pandas as pd
import os

def analyze_excel_file(file_path):
    """分析Excel文件的结构和内容"""
    print(f"\n=== 分析文件: {file_path} ===")
    
    if not os.path.exists(file_path):
        print(f"文件不存在: {file_path}")
        return None
    
    try:
        # 读取Excel文件的所有工作表
        excel_file = pd.ExcelFile(file_path)
        print(f"工作表数量: {len(excel_file.sheet_names)}")
        print(f"工作表名称: {excel_file.sheet_names}")
        
        all_data = {}
        
        for sheet_name in excel_file.sheet_names:
            print(f"\n--- 工作表: {sheet_name} ---")
            df = pd.read_excel(file_path, sheet_name=sheet_name)
            
            print(f"行数: {len(df)}")
            print(f"列数: {len(df.columns)}")
            print(f"列名: {list(df.columns)}")
            
            # 显示前几行数据
            print("\n前5行数据:")
            print(df.head())
            
            # 检查数据类型
            print("\n数据类型:")
            print(df.dtypes)
            
            # 检查空值
            print("\n空值统计:")
            print(df.isnull().sum())
            
            all_data[sheet_name] = df
        
        return all_data
        
    except Exception as e:
        print(f"读取文件时出错: {e}")
        return None

def main():
    """主函数"""
    # 文件路径
    file1 = r"d:\workspace\rag-elasticsearch\src\main\resources\rag\问答知识库.xlsx"
    file2 = r"d:\workspace\rag-elasticsearch\src\main\resources\rag\思考过程补充结果_20251025_095646.xlsx"
    
    # 分析第一个文件
    data1 = analyze_excel_file(file1)
    
    # 分析第二个文件
    data2 = analyze_excel_file(file2)
    
    print("\n=== 分析完成 ===")
    
    return data1, data2

if __name__ == "__main__":
    main()