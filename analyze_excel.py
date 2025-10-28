#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Excel文件分析脚本
用于分析思考过程补充结果Excel文件的内容和结构
"""

import pandas as pd
import sys
import os

def analyze_excel_file(file_path):
    """分析Excel文件的内容和结构"""
    try:
        print(f"正在分析Excel文件: {file_path}")
        print("=" * 60)
        
        # 检查文件是否存在
        if not os.path.exists(file_path):
            print(f"错误: 文件不存在 - {file_path}")
            return
        
        # 读取Excel文件的所有工作表
        excel_file = pd.ExcelFile(file_path)
        print(f"工作表数量: {len(excel_file.sheet_names)}")
        print(f"工作表名称: {excel_file.sheet_names}")
        print()
        
        # 分析每个工作表
        for sheet_name in excel_file.sheet_names:
            print(f"工作表: {sheet_name}")
            print("-" * 40)
            
            # 读取工作表数据
            df = pd.read_excel(file_path, sheet_name=sheet_name)
            
            print(f"行数: {len(df)}")
            print(f"列数: {len(df.columns)}")
            print(f"列名: {list(df.columns)}")
            print()
            
            # 显示数据类型
            print("数据类型:")
            for col in df.columns:
                print(f"  {col}: {df[col].dtype}")
            print()
            
            # 显示前几行数据
            print("前5行数据:")
            print(df.head())
            print()
            
            # 检查空值
            null_counts = df.isnull().sum()
            if null_counts.sum() > 0:
                print("空值统计:")
                for col, count in null_counts.items():
                    if count > 0:
                        print(f"  {col}: {count}")
                print()
            
            # 显示数据样本
            print("数据样本:")
            for i, row in df.head(3).iterrows():
                print(f"第{i+1}行:")
                for col in df.columns:
                    value = row[col]
                    if pd.isna(value):
                        value = "NULL"
                    print(f"  {col}: {value}")
                print()
            
            print("=" * 60)
        
    except Exception as e:
        print(f"分析Excel文件时出错: {str(e)}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    # Excel文件路径
    excel_file_path = r"d:\workspace\rag-elasticsearch\src\main\resources\rag\思考过程补充结果_20251025_095646.xlsx"
    
    analyze_excel_file(excel_file_path)