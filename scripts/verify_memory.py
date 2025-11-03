import argparse
import json
import sys
import time
from uuid import uuid4

import requests


def clear_history(base_url: str, conversation_id: str):
    r = requests.delete(
        f"{base_url}/api/assistant/history",
        params={"conversationId": conversation_id},
        timeout=10,
    )
    if r.status_code >= 400:
        print(f"[WARN] 清空历史失败: {r.status_code} {r.text}")


def chat(base_url: str, session_id: str, query: str, user_id: str = "u1") -> str:
    payload = {
        "tenantCode": "t1",
        "workFlowFlag": "CREDIT",
        "messageType": 0,
        "query": query,
        "userId": user_id,
        "sessionId": session_id,
        "uuid": str(uuid4()),
    }
    r = requests.post(
        f"{base_url}/api/assistant/chat",
        headers={"Content-Type": "application/json"},
        data=json.dumps(payload, ensure_ascii=False),
        timeout=30,
    )
    if r.status_code >= 400:
        raise RuntimeError(f"聊天接口返回错误: {r.status_code} {r.text}")
    return r.text


def get_history(base_url: str, conversation_id: str):
    r = requests.get(
        f"{base_url}/api/assistant/history",
        params={"conversationId": conversation_id},
        timeout=10,
    )
    if r.status_code >= 400:
        raise RuntimeError(f"获取历史失败: {r.status_code} {r.text}")
    return r.json()


def main():
    parser = argparse.ArgumentParser(description="验证聊天记忆保留轮数（max-rounds）")
    parser.add_argument("--base", default="http://localhost:8080", help="服务基址，例如 http://localhost:8080")
    parser.add_argument("--conversation", default="mem-verify", help="会话ID")
    parser.add_argument("--rounds", type=int, default=3, help="发送的对话轮数（每轮包含一次用户提问）")
    args = parser.parse_args()

    base_url = args.base.rstrip("/")
    conv_id = args.conversation

    print(f"[STEP] 清空历史: conversationId={conv_id}")
    clear_history(base_url, conv_id)

    # 使用知识库中已配置为“直接回答”的问题，避免触发外部LLM调用
    queries = [
        "利率是多少",
    ]

    print(f"[STEP] 发送 {args.rounds} 轮对话（仅用户消息，助手回复由系统生成）")
    for i in range(args.rounds):
        q = queries[0]
        print(f"  -> Round {i+1}: 用户: {q}")
        try:
            answer = chat(base_url, conv_id, q)
            print(f"     <- 助手: {answer[:80]}{'...' if len(answer) > 80 else ''}")
        except Exception as e:
            print(f"[ERROR] 发送第 {i+1} 轮失败: {e}")
            print("[HINT] 请确保服务已启动且网络可访问（若需要外部LLM调用）。")
            # 继续下一轮，尽量验证保留逻辑
        time.sleep(0.5)

    print("[STEP] 获取历史")
    history = get_history(base_url, conv_id)
    count = len(history)
    print(f"[RESULT] 历史消息条数: {count}")

    for idx, m in enumerate(history):
        typ = m.get("type", "?")
        content = m.get("content", "")
        snippet = content[:80] + ("..." if len(content) > 80 else "")
        print(f"  [{idx+1}] {typ}: {snippet}")

    print("\n[CHECK] 期望保留最近 2 轮（约 4 条消息）。")
    if count == 4:
        print("[OK] 验证通过：保留消息数为 4（2 轮）")
        sys.exit(0)
    else:
        print("[WARN] 验证未通过：当前保留条数 != 4。请检查配置或实际调用路径。")
        sys.exit(1)


if __name__ == "__main__":
    main()
