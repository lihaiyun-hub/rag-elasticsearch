#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
对 Excel 文件的每个工作表分别进行去重：以“历史提问 + 最新提问”的组合键为准，保留整行所有列，生成新的文件（多工作表输出）。

使用方法：
  1) 安装依赖：
     pip install pandas openpyxl
  2) 运行（默认路径已指向仓库中的文件）：
     python scripts/dedup_questions.py --excel "d:\\workspace\\rag-elasticsearch\\src\\main\\resources\\rag\\意图泛化语料.xlsx"

可选参数：
  --columns      指定用于去重的列名列表（默认：历史提问 最新提问）
  --output       指定输出文件路径（默认：同目录下生成 *_dedup.xlsx，保留原工作表名）
  --drop-empty   去除两列均为空白的行后再去重

兼容参数（旧版）：
  --column       旧版单列去重参数；若提供，将仅按该列进行去重。
"""

import argparse
import os
import sys

import pandas as pd


def main(argv):
    parser = argparse.ArgumentParser(description="按工作表分别去重（历史提问+最新提问组合）")
    parser.add_argument(
        "--excel",
        default=r"d:\\workspace\\rag-elasticsearch\\src\\main\\resources\\rag\\意图泛化语料.xlsx",
        help="Excel 文件路径（xlsx）",
    )
    parser.add_argument(
        "--columns",
        nargs="+",
        default=["历史提问", "最新提问"],
        help="用于去重的列名列表（默认：历史提问 最新提问）",
    )
    # 兼容旧版单列参数：若提供，则覆盖 --columns
    parser.add_argument(
        "--column",
        default=None,
        help="旧版：按单列去重（提供则覆盖 --columns）",
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

    # 若提供旧版 --column，则使用单列去重
    if args.column is not None and args.column.strip() != "":
        args.columns = [args.column.strip()]

    # 读取 Excel 所有工作表
    try:
        sheets: dict[str, pd.DataFrame] = pd.read_excel(args.excel, engine="openpyxl", sheet_name=None)
    except Exception as e:
        print(f"[ERROR] 读取 Excel 失败: {args.excel}. 错误: {e}")
        return 1

    # 输出路径
    if args.output is None:
        base_name = os.path.splitext(os.path.basename(args.excel))[0]
        out_name = f"{base_name}_dedup.xlsx"
        args.output = os.path.join(os.path.dirname(args.excel), out_name)

    # 逐工作表去重并写出到同一输出文件，保留原工作表名
    totals = []
    uniques = []
    with pd.ExcelWriter(args.output, engine="openpyxl") as writer:
        for sheet_name, df in sheets.items():
            # 检查列
            missing = [col for col in args.columns if col not in df.columns]
            if missing:
                print(f"[WARN] 工作表 '{sheet_name}' 缺少列: {missing}。跳过去重，原样写出。")
                df.to_excel(writer, sheet_name=sheet_name, index=False)
                totals.append(len(df))
                uniques.append(len(df))
                continue

            # 规范化各列
            norm_cols = {}
            for col in args.columns:
                norm_cols[col] = df[col].astype(str).fillna("").str.strip()
            norm_df = pd.DataFrame(norm_cols)

            # 可选：去除所有用于去重的列均为空的行
            if args.drop_empty:
                empty_mask = (norm_df == "").all(axis=1)
                df = df.loc[~empty_mask].copy()
                norm_df = norm_df.loc[~empty_mask].copy()

            # 构造组合键并去重（保留首个出现）
            # 高效拼接：逐列累积连接，避免逐行 apply
            combined = None
            for i, col in enumerate(args.columns):
                series = norm_df[col]
                if combined is None:
                    combined = series
                else:
                    combined = combined + "||" + series
            df["__norm_key__"] = combined if combined is not None else ""
            dedup_df = df.drop_duplicates(subset=["__norm_key__"], keep="first")

            # 写出（移除内部规范化键）
            out_df = dedup_df.drop(columns=["__norm_key__"], errors="ignore")
            out_df.to_excel(writer, sheet_name=sheet_name, index=False)

            totals.append(len(df))
            uniques.append(len(out_df))

    # 输出总结
    print(f"完成去重：已输出到：{args.output}")
    for (sheet_name, df), total, unique in zip(sheets.items(), totals, uniques):
        print(f"  - 工作表 '{sheet_name}': 总计 {total}，唯一 {unique}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
