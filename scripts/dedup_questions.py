#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
对 Excel 文件的“最新提问”列进行去重，保留整行所有列，生成新的文件。

使用方法：
  1) 安装依赖：
     pip install pandas openpyxl
  2) 运行（默认路径已指向仓库中的文件）：
     python scripts/dedup_questions.py --excel "d:\\workspace\\rag-elasticsearch\\src\\main\\resources\\rag\\意图泛化语料.xlsx"

可选参数：
  --column       指定去重的列名（默认：最新提问）
  --output       指定输出文件路径（默认：同目录下生成 *_dedup.xlsx）
  --drop-empty   去除空白行后再去重
"""

import argparse
import os
import sys

import pandas as pd


def main(argv):
    parser = argparse.ArgumentParser(description="最新提问列去重，生成新文件")
    parser.add_argument(
        "--excel",
        default=r"d:\\workspace\\rag-elasticsearch\\src\\main\\resources\\rag\\意图泛化语料.xlsx",
        help="Excel 文件路径（xlsx）",
    )
    parser.add_argument(
        "--column",
        default="最新提问",
        help="需要去重的列名",
    )
    parser.add_argument(
        "--output",
        default=None,
        help="输出文件路径（默认：同目录下 *_dedup.xlsx）",
    )
    parser.add_argument(
        "--drop-empty",
        action="store_true",
        help="去除空白值后再去重",
    )
    args = parser.parse_args(argv)

    # 读取 Excel
    try:
        df = pd.read_excel(args.excel, engine="openpyxl")
    except Exception as e:
        print(f"[ERROR] 读取 Excel 失败: {args.excel}. 错误: {e}")
        return 1

    if args.column not in df.columns:
        print(f"[ERROR] Excel 中缺少列: {args.column}. 现有列: {list(df.columns)}")
        return 1

    # 基于指定列规范化并去重（保留整行所有列）
    # 可选：去除空白值后再去重
    norm_series = df[args.column].astype(str).str.strip()
    if args.drop_empty:
        mask = norm_series != ""
        df = df[mask].copy()
        norm_series = df[args.column].astype(str).str.strip()

    # 构造规范化键并按该键去重，保留首个出现
    df["__norm_key__"] = norm_series
    dedup_df = df.drop_duplicates(subset=["__norm_key__"], keep="first")

    # 输出路径
    if args.output is None:
        base_name = os.path.splitext(os.path.basename(args.excel))[0]
        out_name = f"{base_name}_dedup.xlsx"
        args.output = os.path.join(os.path.dirname(args.excel), out_name)

    # 写出 Excel，保留所有原有列（移除内部规范化键）
    out_df = dedup_df.drop(columns=["__norm_key__"], errors="ignore")
    try:
        out_df.to_excel(args.output, index=False)
    except Exception as e:
        print(f"[ERROR] 写出 Excel 失败: {args.output}. 错误: {e}")
        return 1

    total = len(df)
    unique = len(out_df)
    print(f"完成去重：总计 {total}，唯一 {unique}，已输出到：{args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
