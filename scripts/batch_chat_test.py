#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
批量请求聊天接口并断言返回值是否与 Excel 中的“回答”一致。

支持读取三张 Sheet：通用、已授信、未授信，并根据 Sheet 设置
workFlowFlag：通用=02（默认）、已授信=02、未授信=01。

使用方法：
  1) 安装依赖：
     pip install requests pandas openpyxl
  2) 运行：
     python scripts/batch_chat_test.py --excel "d:\\workspace\\rag-elasticsearch\\src\\main\\resources\\rag\\意图泛化语料.xlsx" --url http://localhost:8080/api/assistant/chat

可选参数：
  --limit   仅测试前 N 条记录（跨 Sheet 合并后）
"""

import argparse
import json
import sys
import time
import math
from typing import List, Tuple, Dict, Any

import pandas as pd
import requests


def load_excel_pairs(excel_path: str) -> List[Dict[str, Any]]:
    """读取 Excel 的三张 Sheet，返回包含 sheet、question、expected 的列表。"""
    try:
        sheets: Dict[str, pd.DataFrame] = pd.read_excel(
            excel_path, sheet_name=None, engine="openpyxl"
        )
    except Exception as e:
        raise RuntimeError(f"读取 Excel 失败: {excel_path}. 错误: {e}")

    target_sheets = ["通用", "已授信", "未授信"]
    records: List[Dict[str, Any]] = []
    required_cols = ["最新提问", "回答"]

    for sheet_name in target_sheets:
        if sheet_name not in sheets:
            # 不存在的 Sheet 跳过（允许只提供部分 Sheet）
            continue
        df = sheets[sheet_name]
        for col in required_cols:
            if col not in df.columns:
                raise RuntimeError(
                    f"Sheet '{sheet_name}' 中缺少必需列: {col}. 现有列: {list(df.columns)}"
                )

        # 转成字符串并去除首尾空白
        questions = df["最新提问"].astype(str).str.strip().tolist()
        answers = df["回答"].astype(str).str.strip().tolist()

        for q, a in zip(questions, answers):
            records.append({
                "sheet": sheet_name,
                "question": q,
                "expected": a,
            })

    if not records:
        raise RuntimeError(
            f"未在 Excel 中找到目标 Sheet: {target_sheets}. 现有 Sheet: {list(sheets.keys())}"
        )

    return records


def make_payload(user_message: str, work_flow_flag: str) -> Dict[str, Any]:
    """构造请求体，根据 sheet 设置 workFlowFlag，并将提问作为 query。"""
    return {
        "tenantCode": "YYH",
        "sessionId": "test_a9104d8f",
        "uuid": "asdadasdasfnasjdfnsadj",
        "userId": "djasjdkadkasjnkas",
        "query": user_message,
        "maxPrice": 50000.0,
        "workFlowFlag": work_flow_flag,
        "messageType": 2,
        "loanInfo": {
            "maxPrice": "50000",
            "contractNum": "jasndadnakj",
            "terms": [3, 6, 9, 12],
            "loanPurseCode": "02",
        },
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

    # Sheet 到 workFlowFlag 的映射
    SHEET_WORKFLOW_FLAG = {"通用": "02", "已授信": "02", "未授信": "01"}

    print(f"开始测试，共 {total} 条（合并三张 Sheet）")
    start_total = time.perf_counter()
    durations: List[float] = []
    for idx, item in enumerate(pairs, start=1):
        question = item["question"]
        expected = item["expected"]
        sheet = item["sheet"]
        wf = SHEET_WORKFLOW_FLAG.get(sheet, "02")
        payload = make_payload(question, wf)
        case_start = time.perf_counter()
        try:
            actual = call_api(args.url, payload)
        except requests.RequestException as e:
            print(f"[{idx}/{total}] FAIL - 请求异常: {e} ({sheet}, workFlowFlag={wf})")
            durations.append(time.perf_counter() - case_start)
            failed_cases.append({
                "userMessage": question,
                "sheet": sheet,
                "workFlowFlag": wf,
                "error": str(e),
            })
            continue

        ok = actual.strip() == expected.strip()
        duration = time.perf_counter() - case_start
        durations.append(duration)
        if ok:
            passed += 1
            print(f"[{idx}/{total}] PASS ({sheet}, workFlowFlag={wf})")
        else:
            print(f"[{idx}/{total}] FAIL ({sheet}, workFlowFlag={wf})")
            failed_cases.append({
                "userMessage": question,
                "sheet": sheet,
                "workFlowFlag": wf,
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

    # 监控指标：总用时、平均用时、最长用时
    total_time = time.perf_counter() - start_total
    avg_time = (sum(durations) / len(durations)) if durations else 0.0
    max_time = max(durations) if durations else 0.0
    print(f"- 总用时: {total_time:.3f}s")
    print(f"- 平均用时/条: {avg_time:.3f}s")
    print(f"- 最长用时: {max_time:.3f}s")

    # P95/P99 指标（最近秩法）
    if durations:
        sd = sorted(durations)
        idx99 = math.ceil(0.99 * len(sd)) - 1
        idx99 = max(0, min(idx99, len(sd) - 1))
        p99 = sd[idx99]
        idx95 = math.ceil(0.95 * len(sd)) - 1
        idx95 = max(0, min(idx95, len(sd) - 1))
        p95 = sd[idx95]
    else:
        p99 = 0.0
        p95 = 0.0
    print(f"- P95用时: {p95:.3f}s")
    print(f"- P99用时: {p99:.3f}s")

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

