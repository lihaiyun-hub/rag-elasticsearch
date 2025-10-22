import React, { useState, useRef, useEffect } from 'react';
import { Card, Input, Button, List, Avatar } from 'antd';
import { RobotOutlined, RollbackOutlined, ReloadOutlined, PlusOutlined, ArrowUpOutlined } from '@ant-design/icons';
import CreditStepCard from './CreditStepCard';

const ChatWindow = ({ messages = [], onSend, loading, stepData, onComplete, onRollback, onRestart, canRollback, onAssistantReply }) => {
  const [input, setInput] = useState('');
  const listRef = useRef(null);

  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight;
    }
  }, [messages, stepData]);

  const handleSend = () => {
    const text = input.trim();
    if (!text) return;
    onSend && onSend(text);
    setInput('');
    // 由上层决定是否调用后端，若提供onAssistantReply用于外部注入机器人回复
    if (onAssistantReply) {
      onAssistantReply(text);
    }
  };

  const bubbleStyle = (isUser) => ({
    maxWidth: '85%',
    background: isUser ? '#1890ff' : '#fff',
    color: isUser ? '#fff' : '#333',
    border: isUser ? 'none' : '1px solid #f0f0f0',
    borderRadius: 12,
    padding: '10px 12px',
    boxShadow: isUser ? 'none' : '0 1px 3px rgba(0,0,0,0.04)'
  });

  return (
    <Card className="chat-window" style={{ width: '100%', height: '100vh', margin: '0 auto' }} styles={{ body: { display: 'flex', flexDirection: 'column', height: '100%' } }}>
      <div
        ref={listRef}
        style={{
          flex: 1,
          minHeight: 0,
          overflowY: 'auto',
          padding: '12px 16px',
          background: '#fafafa',
          borderRadius: 8,
          border: '1px solid #f0f0f0'
        }}
      >
        {/* 已移除顶部操作按钮，仅保留纯聊天窗口 */}
        {false && (
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginBottom: 8 }}>
            <Button size="small" icon={<RollbackOutlined />} disabled={!canRollback} onClick={onRollback}>
              返回上一步
            </Button>
            <Button size="small" icon={<ReloadOutlined />} onClick={onRestart} disabled={loading}>
              重新开始
            </Button>
          </div>
        )}

        {/* 移除欢迎消息，不再在空对话时渲染任何提示 */}

        {messages && messages.length > 0 && (
          <List
            dataSource={messages}
            locale={{ emptyText: null }}
            renderItem={(msg) => {
              const isUser = msg.role === 'user';
              const hasCard = !!msg.card;
              // 合并卡片与文案的助手消息体
              if (!isUser && hasCard) {
                return (
                  <List.Item style={{ border: 'none', padding: '8px 0' }}>
                    <div
                      style={{
                        display: 'flex',
                        justifyContent: 'flex-start',
                        gap: 8,
                        alignItems: 'flex-start',
                        width: '100%',
                      }}
                    >
                      <Avatar
                        size={28}
                        src={`${process.env.PUBLIC_URL}/avatars/assistant.svg`}
                        alt="assistant"
                      />
                      <div style={{ maxWidth: '75%' }}>
                        {/* 外层气泡：将文案和卡片一起包裹，其他样式保持不变 */}
                        <div style={{ ...bubbleStyle(false) }}>
                          {msg.content && (
                            <div style={{ width: '100%', marginBottom: 8 }}>{msg.content}</div>
                          )}
                          <CreditStepCard stepData={msg.card} onComplete={onComplete} loading={loading} />
                        </div>
                      </div>
                    </div>
                  </List.Item>
                );
              }

              // 常规文本消息
              return (
                <List.Item style={{ border: 'none', padding: '8px 0' }}>
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: isUser ? 'flex-end' : 'flex-start',
                      gap: 8,
                      alignItems: 'flex-start',
                      width: '100%',
                    }}
                  >
                    {!isUser && (
                      <Avatar
                        size={28}
                        src={`${process.env.PUBLIC_URL}/avatars/assistant.svg`}
                        alt="assistant"
                      />
                    )}
                    <div style={bubbleStyle(isUser)}>{msg.content}</div>
                    {isUser && (
                      <Avatar
                        size={28}
                        src={`${process.env.PUBLIC_URL}/avatars/user.svg`}
                        alt="user"
                      />
                    )}
                  </div>
                </List.Item>
              );
            }}
          />
        )}

        {/* 已隐藏：单独基于 stepData 的卡片渲染，避免与消息体重复 */}
        {false && stepData && (
          <div style={{ display: 'flex', justifyContent: 'flex-start', padding: '8px 0', gap: 8 }}>
            <Avatar size={28} icon={<RobotOutlined />} />
            <div style={{ maxWidth: '85%' }}>
              <CreditStepCard stepData={stepData} onComplete={onComplete} loading={loading} />
            </div>
          </div>
        )}

        {/* 已隐藏授信步骤卡片（旧） */}
        {false && stepData && null}
      </div>

      <div style={{ display: 'flex', alignItems: 'center', padding: '12px 16px', gap: 8 }}>
        <Input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="请输入消息..."
          onPressEnter={handleSend}
          disabled={loading}
        />
        {/* 恢复输入栏的快捷按钮样式：加号方形按钮 */}
        <Button
          disabled={loading}
          icon={<PlusOutlined />}
          style={{ width: 32, height: 32, borderRadius: 8 }}
        />
        <Button type="primary" onClick={handleSend} disabled={loading} icon={<ArrowUpOutlined />}>发送</Button>
      </div>
    </Card>
  );
};

export default ChatWindow;