#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
批量请求聊天接口并断言返回值是否与 Excel 中的“回答”一致。

使用方法：
  1) 安装依赖：
     pip install requests pandas openpyxl
  2) 运行：
     python scripts/batch_chat_test.py --excel "d:\\workspace\\rag-elasticsearch\\src\\main\\resources\\rag\\意图泛化语料.xlsx" --url http://localhost:8080/api/assistant/chat

可选参数：
  --limit   仅测试前 N 条记录
"""

import argparse
import json
import sys
from typing import List, Tuple, Dict, Any

import pandas as pd
import requests


def load_excel_pairs(excel_path: str) -> List[Tuple[str, str]]:
    """读取 Excel，返回 (最新提问, 回答) 列表。"""
    try:
        df = pd.read_excel(excel_path, engine="openpyxl")
    except Exception as e:
        raise RuntimeError(f"读取 Excel 失败: {excel_path}. 错误: {e}")

    required_cols = ["最新提问", "回答"]
    for col in required_cols:
        if col not in df.columns:
            raise RuntimeError(f"Excel 中缺少必需列: {col}. 现有列: {list(df.columns)}")

    # 转成字符串并去除首尾空白
    questions = df["最新提问"].astype(str).str.strip().tolist()
    answers = df["回答"].astype(str).str.strip().tolist()

    pairs = list(zip(questions, answers))
    return pairs


def make_payload(user_message: str) -> Dict[str, Any]:
    """构造请求体。"""
    return {
        "chatId": "test_a9104d8f",
        "userMessage": user_message,
        "userName": "张三",
        "availableCredit": 50000.0,
        "recentRepaymentStatus": "正常",
        "authorized": True,
        "termOptions": [3, 6, 9, 12],
        "loanPurposes": ["消费", "装修", "教育", "旅游"],
        "bankCardNumber": "1234",
        "bankName": "工商银行",
    }


def call_api(url: str, payload: Dict[str, Any], timeout: float = 30.0) -> str:
    """调用接口，返回响应文本。"""
    resp = requests.post(url, json=payload, timeout=timeout)
    resp.raise_for_status()
    return resp.text.strip()


def main(argv: List[str]) -> int:
    parser = argparse.ArgumentParser(description="批量测试聊天接口并统计通过率")
    parser.add_argument(
        "--excel",
        default=r"d:\\workspace\\rag-elasticsearch\\src\\main\\resources\\rag\\意图泛化语料.xlsx",
        help="Excel 文件路径（包含'最新提问'和'回答'两列）",
    )
    parser.add_argument(
        "--url",
        default="http://localhost:8080/api/assistant/chat",
        help="聊天接口 URL",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="仅测试前 N 条记录",
    )
    args = parser.parse_args(argv)

    try:
        pairs = load_excel_pairs(args.excel)
    except Exception as e:
        print(f"[ERROR] {e}")
        return 1

    if args.limit is not None:
        pairs = pairs[: args.limit]

    total = len(pairs)
    if total == 0:
        print("[WARN] Excel 中没有有效数据")
        return 0

    passed = 0
    failed_cases: List[Dict[str, Any]] = []

    print(f"开始测试，共 {total} 条")
    for idx, (question, expected) in enumerate(pairs, start=1):
        payload = make_payload(question)
        try:
            actual = call_api(args.url, payload)
        except requests.RequestException as e:
            print(f"[{idx}/{total}] FAIL - 请求异常: {e}")
            failed_cases.append({
                "userMessage": question,
                "error": str(e),
            })
            continue

        ok = actual.strip() == expected.strip()
        if ok:
            passed += 1
            print(f"[{idx}/{total}] PASS")
        else:
            print(f"[{idx}/{total}] FAIL")
            failed_cases.append({
                "userMessage": question,
                "expected": expected,
                "actual": actual,
            })

    failed = total - passed
    pass_rate = (passed / total) if total else 0.0
    print("\n测试完成：")
    print(f"- Total: {total}")
    print(f"- Passed: {passed}")
    print(f"- Failed: {failed}")
    print(f"- Pass Rate: {pass_rate:.2%}")

    if failed_cases:
        print("\n不通过的案例 userMessage：")
        for case in failed_cases:
            print(f"- {case.get('userMessage', '')}")

        # 保存详细不通过信息到文件
        with open("failed_cases.json", "w", encoding="utf-8") as f:
            json.dump(failed_cases, f, ensure_ascii=False, indent=2)
        print("已保存详细失败案例到文件: failed_cases.json")

    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

