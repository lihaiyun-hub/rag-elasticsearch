import React, { useState, useRef, useEffect } from 'react';
import { Card, Input, Button, List, Spin, Avatar, Alert } from 'antd';
import { RobotOutlined, RollbackOutlined, ReloadOutlined, CreditCardOutlined, ThunderboltOutlined, PlusOutlined, AudioOutlined, ArrowUpOutlined } from '@ant-design/icons';
import CreditStepCard from './CreditStepCard';

const ChatWindow = ({ messages = [], onSend, loading, stepData, onComplete, onRollback, onRestart, canRollback, onStartCredit, onAssistantReply }) => {
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
    maxWidth: '75%',
    background: isUser ? '#1890ff' : '#fff',
    color: isUser ? '#fff' : '#333',
    border: isUser ? 'none' : '1px solid #f0f0f0',
    borderRadius: 12,
    padding: '10px 12px',
    boxShadow: isUser ? 'none' : '0 1px 3px rgba(0,0,0,0.04)'
  });

  const renderWelcome = () => (
    <div style={{ display: 'flex', justifyContent: 'flex-start', padding: '8px 0', gap: 8 }}>
      <Avatar size={28} icon={<RobotOutlined />} />
      <div style={{ maxWidth: '75%' }}>
        <Alert
          type="info"
          message="欢迎使用智能助手"
          description={
            <div>
              <div style={{ marginBottom: 8 }}>我可以帮助你：咨询问题、查询信息、以及在需要时办理授信流程。</div>
              <div style={{ display: 'flex', gap: 8 }}>
                <Button type="primary" icon={<CreditCardOutlined />} size="small" onClick={onStartCredit} disabled={loading}>
                  开始授信流程
                </Button>
                <Button icon={<ThunderboltOutlined />} size="small" disabled>
                  更多功能（敬请期待）
                </Button>
              </div>
            </div>
          }
          showIcon
          style={{ background: '#fff', borderRadius: 12 }}
        />
      </div>
    </div>
  );

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

        {/* 欢迎态已隐藏 */}
        {false && !stepData && renderWelcome()}

        {messages && messages.length > 0 && (
          <List
            dataSource={messages}
            locale={{ emptyText: null }}
            renderItem={(msg) => {
              const isUser = msg.role === 'user';
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

        {/* 将当前步骤卡片作为助手消息渲染到聊天窗口中 */}
        {stepData && (
          <div style={{ display: 'flex', justifyContent: 'flex-start', padding: '8px 0', gap: 8 }}>
            <Avatar size={28} icon={<RobotOutlined />} />
            <div style={{ maxWidth: '75%' }}>
              <CreditStepCard stepData={stepData} onComplete={onComplete} loading={loading} />
            </div>
          </div>
        )}
        {/* 已隐藏授信步骤卡片 */}
        {false && stepData && (
          <div style={{ display: 'flex', justifyContent: 'flex-start', padding: '8px 0', gap: 8 }}>
            <Avatar size={28} icon={<RobotOutlined />} />
            <div style={{ maxWidth: '75%' }}>
              <CreditStepCard stepData={stepData} onComplete={onComplete} loading={loading} />
            </div>
          </div>
        )}

        {/* 助手处理中提示 */}
        {loading && (
          <div style={{ display: 'flex', justifyContent: 'flex-start', padding: '8px 0', gap: 8 }}>
            <Avatar size={28} icon={<RobotOutlined />} />
            <div
              style={{
                maxWidth: '75%',
                background: '#fff',
                color: '#333',
                border: '1px solid #f0f0f0',
                borderRadius: 12,
                padding: '10px 12px',
                boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
                display: 'inline-flex',
                alignItems: 'center',
                gap: 8
              }}
            >
              <Spin size="small" />
              <span>助手正在处理，请稍候...</span>
            </div>
          </div>
        )}
      </div>

      <div style={{ display: 'flex', justifyContent: 'center', marginTop: 12 }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            width: '100%',
            maxWidth: '100%',
            background: '#fff',
            border: '1px solid #f0f0f0',
            borderRadius: 28,
            boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
            padding: '8px 12px',
          }}
        >
          <Button
            type="text"
            icon={<PlusOutlined />}
            disabled={loading}
            style={{ width: 32, height: 32, borderRadius: 16 }}
          />
          <Input.TextArea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onPressEnter={(e) => {
              if (!e.shiftKey) {
                e.preventDefault();
                handleSend();
              }
            }}
            placeholder="请输入与助手的对话内容..."
            autoSize={{ minRows: 1, maxRows: 4 }}
            disabled={loading}
            style={{
              flex: 1,
              border: 'none',
              outline: 'none',
              boxShadow: 'none',
              background: 'transparent',
              resize: 'none',
              padding: 0,
            }}
          />
          <Button
            type="text"
            icon={<AudioOutlined />}
            disabled
            style={{ width: 32, height: 32 }}
          />
          <Button
            shape="circle"
            icon={<ArrowUpOutlined />}
            onClick={handleSend}
            disabled={loading || !input.trim()}
            style={{
              background: '#000',
              color: '#fff',
              width: 36,
              height: 36,
              boxShadow: '0 2px 6px rgba(0,0,0,0.2)',
            }}
          />
        </div>
      </div>
    </Card>
  );
};

export default ChatWindow;