import React, { useState, useEffect } from 'react';
import { Steps, message, Spin, Button, Card, Tooltip } from 'antd';
import { ReloadOutlined, InfoCircleOutlined } from '@ant-design/icons';
import SuccessPage from './components/SuccessPage';
import { consumerCreditAPI } from './services/api';
import './App.css';
import ChatWindow from './components/ChatWindow';
import { assistantAPI } from './services/api';

const { Step } = Steps;

function App() {
  const [currentStep, setCurrentStep] = useState(0);
  const [chatId, setChatId] = useState('');
  const [loading, setLoading] = useState(false);
  const [stepData, setStepData] = useState(null);
  const [authorized, setAuthorized] = useState(false);
  const [error, setError] = useState(null);
  const [messages, setMessages] = useState([]);
  const [stepHistory, setStepHistory] = useState([]);

  // 请求配置：用户上下文与流程起始步骤
  const START_STEP_OPTIONS = Array.from({ length: 8 }, (_, i) => i + 1);
  const REPAYMENT_STATUS_OPTIONS = ['正常', '逾期', '已结清'];
  const AUTH_STATUS_OPTIONS = ['未授信', '已授信'];
  const defaultConfig = {
    startStep: 1,
    userName: 'John Doe',
    availableCredit: '5000',
    currentLoanPlan: '无',
    recentRepaymentStatus: '正常',
    maxLoanAmount: '20000',
    authorizationStatus: '未授信'
  };
  const [config, setConfig] = useState(defaultConfig);
  // 卡片键、名称与提示
  const getCardKey = (card) => card?.cardId || card?.id;
  const cardNameMap = {
    live_detect: '活体检测',
    id_upload: '上传身份证',
    set_password: '设置密码',
    sign_agreement: '签署协议',
    bank_bind: '绑定银行卡',
    occupation: '职业信息',
    contact: '联系人',
    assess_wait: '系统评估',
    consumer_loan_offers: '授信完成'
  };
  const getCardName = (key) => cardNameMap[key] || '未知步骤';
  const cardTipMap = {
    live_detect: '请确保光线充足、正对摄像头，保持静止并根据提示完成动作。',
    id_upload: '请上传清晰的身份证正反面照片，确保边角完整、文字清晰。',
    set_password: '设置6位数字交易密码，妥善保管，不要与他人共享。',
    sign_agreement: '请认真阅读协议内容，勾选同意后继续流程。',
    bank_bind: '请准备本人银行卡信息，确保预留手机号可接收短信。',
    occupation: '填写真实有效的职业与收入信息，有助于评估授信额度。',
    contact: '请填写两位可联系到的紧急联系人，留存准确的手机号。',
    assess_wait: '系统正在评估，请耐心等待，通常不超过30秒。',
    consumer_loan_offers: '评估已完成，请选择适合您的借款方案。'
  };
  const getCardTip = (key) => cardTipMap[key] || '';

  // 生成唯一会话ID并发送欢迎消息（不自动进入授信流程）
  useEffect(() => {
    const generateChatId = () => {
      const timestamp = Date.now();
      const random = Math.random().toString(36).substr(2, 9);
      return `user_${timestamp}_${random}`;
    };
    const id = generateChatId();
    setChatId(id);
    // 初始不注入助手消息，保持纯净对话窗口
    setMessages([]);
  }, []);

  const addAssistantMessage = (content) => {
    setMessages((prev) => [...prev, { role: 'assistant', content }]);
  };
  const addUserMessage = (content) => {
    setMessages((prev) => [...prev, { role: 'user', content }]);
  };


  const initializeCreditProcess = async () => {
    try {
      setLoading(true);
      setError(null);
      const startStep = Number(config.startStep) || 1;
      const response = await consumerCreditAPI.startCreditProcess(chatId, startStep);
      if (response.data && response.data.card) {
        const card = response.data.card;
        setStepData(card);
        setCurrentStep(0);
        setAuthorized(false);
        setStepHistory([card]);
        const key = getCardKey(card);
        addAssistantMessage(`${getCardName(key)}开始。${getCardTip(key)}`);
      } else {
        throw new Error('响应格式错误：缺少card字段');
      }
    } catch (error) {
      setError('初始化授信流程失败');
      message.error('初始化授信流程失败');
    } finally {
      setLoading(false);
    }
  };

  const handleStepComplete = async (formData) => {
    try {
      setLoading(true);
      const currKey = getCardKey(stepData);
      // 若为借款方案卡片，执行确认借款逻辑而不是调用授信步骤接口
      if (currKey === 'consumer_loan_offers') {
        // 获取用户在卡片中选择的下拉值，用于提交后端或记录
        const term = formData?.termMonths;
        const account = formData?.account;
        const purpose = formData?.purpose;
        const summary = [
          term ? `${term}个月` : null,
          account || null,
          purpose || null
        ].filter(Boolean).join(' · ');
        addAssistantMessage(`好的，已确认借款请求${summary ? `（${summary}）` : ''}，我们会尽快为您办理。`);
        message.success('您已提交借款申请');
        return;
      }
      // 调用后端完成当前步骤
      const response = await consumerCreditAPI.completeStep(chatId, currentStep);
      if (response.data && response.data.card) {
        const nextCard = response.data.card;
        const nextKey = getCardKey(nextCard);
        // 记录步骤完成状态
        addAssistantMessage(`已完成【${getCardName(currKey)}】。即将进入【${getCardName(nextKey)}】。${getCardTip(nextKey)}`);
        // 检查是否完成
        if (nextCard.state === 'COMPLETED' || nextCard.state === 'CREDIT_DONE' || nextKey === 'consumer_loan_offers') {
          setAuthorized(true);
          // 已移除弹窗：message.success('授信申请已通过！');
        } else {
          // 进入下一步
          setStepData(nextCard);
          setCurrentStep((s) => s + 1);
          setStepHistory((h) => [...h, nextCard]);
          message.success('步骤完成，进入下一步');
        }
      } else {
        console.error('响应格式错误:', response.data);
        throw new Error('响应格式错误：缺少card字段');
      }
    } catch (error) {
      console.error('步骤完成失败:', error);
      message.error('步骤完成失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  const handleSelectOffer = (offer) => {
    message.success(`已选择借款方案：${(offer.amount / 10000).toFixed(1)}万元`);
  };

  const handleRefresh = () => {
    window.location.reload();
  };

  const handleResetConfig = () => {
    setConfig({ ...defaultConfig });
  };

  const generateRandomName = () => {
    const surnames = ['张','李','王','刘','陈','杨','黄','赵','周','吴','徐','孙','马','朱','胡','郭','何','高','林','罗'];
    const givenChars = ['伟','芳','娜','敏','静','丽','强','磊','军','洋','勇','艳','杰','涛','明','超','秀英','霞','平','红','丹','玲','成','刚','佳','妍','涵','媛','博','晨','宇','轩','然','雅','宁'];
    const surname = surnames[Math.floor(Math.random() * surnames.length)];
    const len = Math.random() < 0.5 ? 1 : 2;
    let given = '';
    for (let i = 0; i < len; i++) {
      given += givenChars[Math.floor(Math.random() * givenChars.length)];
    }
    return surname + given;
  };

  const getStepTitle = (step) => {
    const titles = [
      '活体检测',
      '上传身份证',
      '设置密码',
      '签署协议',
      '绑定银行卡',
      '职业信息',
      '联系人',
      '系统评估',
      '授信完成'
    ];
    return titles[step] || `步骤${step + 1}`;
  };

  const handleChatSend = async (text) => {
    addUserMessage(text);
    // 删除前端意图识别，统一由后端进行意图判断与流程路由
    try {
      setLoading(true);
      const reply = await assistantAPI.chat(chatId, text, {
        userName: config.userName || undefined,
        availableCredit: config.availableCredit !== '' ? Number(config.availableCredit) : undefined,
        currentLoanPlan: config.currentLoanPlan || undefined,
        recentRepaymentStatus: config.recentRepaymentStatus || undefined,
        maxLoanAmount: config.maxLoanAmount !== '' ? Number(config.maxLoanAmount) : undefined,
        authorized: config.authorizationStatus === '已授信'
      });
      // 适配结构化返回：优先渲染文本，其次根据card决定是否渲染步骤卡片或切换到授信完成页
      if (reply && typeof reply === 'object') {
        const { text: botText, card } = reply;
        if (botText) {
          addAssistantMessage(botText);
        }
        if (card) {
          const key = getCardKey(card);
          // 忽略纯文本/错误卡片
          if (key && key !== 'text_response' && key !== 'error') {
            // 授信已完成：在聊天窗口渲染借款方案卡片
            if (key === 'consumer_loan_offers' || card.state === 'CREDIT_DONE' || card.state === 'COMPLETED') {
              setAuthorized(true);
              setStepData(card);
              setCurrentStep(0);
              setStepHistory([card]);
              // 优化：仅在无文本回复时补充一条提示，避免重复机器人文案
              if (!botText) {
                addAssistantMessage('授信已完成，以下为为您推荐的借款方案');
              }
              // 已移除弹窗：message.success('授信申请已通过！');
            } else {
              // 渲染流程步骤卡片到聊天窗口
              setStepData(card);
              setCurrentStep(0);
              setStepHistory([card]);
              addAssistantMessage(`${getCardName(key)}开始。${getCardTip(key)}`);
            }
          }
        }
      } else {
        // 兼容后端返回纯字符串的情况
        addAssistantMessage(reply || '助手暂时无法回答，请稍后再试。');
      }
    } catch (e) {
      addAssistantMessage('抱歉，当前服务繁忙或工具调用出现问题，请稍后重试。');
    } finally {
      setLoading(false);
    }
  };

  const handleRestart = async () => {
    if (loading) return;
    setMessages((prev) => [...prev, { role: 'assistant', content: '已重置当前会话。若需要办理授信，请点击开始或告诉我。' }]);
    setStepHistory([]);
    setAuthorized(false);
    setCurrentStep(0);
    setStepData(null);
  };

  const handleRollback = () => {
    if (loading || authorized) return;
    if (stepHistory.length <= 1) return;
    const newHistory = [...stepHistory];
    newHistory.pop();
    const prevCard = newHistory[newHistory.length - 1];
    setStepHistory(newHistory);
    setStepData(prevCard);
    setCurrentStep((s) => (s > 0 ? s - 1 : 0));
    const prevKey = getCardKey(prevCard);
    addAssistantMessage(`已返回到【${getCardName(prevKey)}】。${getCardTip(prevKey)}`);
  };

  const canRollback = stepHistory.length > 1 && !loading && !authorized;

  const handleStartCredit = async () => {
    if (loading || authorized) return;
    if (!stepData) {
      addAssistantMessage('好的，正在为您开启授信流程。');
      await initializeCreditProcess();
    }
  };

  if (error) {
    return (
      <div className="error-container">
        <Card>
          <div style={{ textAlign: 'center', padding: '48px' }}>
            <div style={{ fontSize: 64, marginBottom: 24 }}>❌</div>
            <h3>出错了</h3>
            <p style={{ color: '#666', marginBottom: 24 }}>{error}</p>
            <Button 
              type="primary" 
              icon={<ReloadOutlined />}
              onClick={handleRefresh}
            >
              刷新页面
            </Button>
          </div>
        </Card>
      </div>
    );
  }

  // 纯聊天模式：不使用全屏加载动画，交给 ChatWindow 内的内联加载提示处理
  if (false && loading && !stepData) {
    return (
      <div className="loading-container">
        <Spin size="large" spinning={true}>
          <div style={{ height: 200 }} />
        </Spin>
      </div>
    );
  }

  if (authorized && (!stepData || getCardKey(stepData) !== 'consumer_loan_offers')) {
    return <SuccessPage onSelectOffer={handleSelectOffer} chatId={chatId} />;
  }

  return (
    <div className="App">
      <div className="container layout">
        {/* 左侧配置栏（侧边栏） */}
        <div className="sidebar">
          <Card style={{ height: '100%' }}>
            {/* 移除卡片右上角的全局信息标识 */}
            <div className="config-form">
              <div className="config-row">
                <span className="config-label">
                  Start Step
                  <Tooltip title="选择授信流程的起始步骤，用于模拟从不同阶段进入流程">
                    <InfoCircleOutlined className="label-info" />
                  </Tooltip>
                </span>
                <select
                  value={config.startStep}
                  onChange={(e) => setConfig({ ...config, startStep: e.target.value })}
                  className="config-input"
                >
                  {START_STEP_OPTIONS.map((opt) => (
                    <option key={opt} value={opt}>{opt}</option>
                  ))}
                </select>
              </div>
              <div className="config-row">
                <span className="config-label">
                  User Name
                  <Tooltip title="用户姓名，仅用于个性化称呼，不影响授信逻辑">
                    <InfoCircleOutlined className="label-info" />
                  </Tooltip>
                </span>
                <div className="config-input-wrapper">
                  <input type="text" value={config.userName}
                    onChange={(e) => setConfig({ ...config, userName: e.target.value })}
                    className="config-input" />
                  <Button
                    type="text"
                    size="small"
                    icon={<ReloadOutlined />}
                    className="input-action-button"
                    onClick={() => setConfig({ ...config, userName: generateRandomName() })}
                    title="随机生成姓名"
                  />
                </div>
              </div>
              <div className="config-row">
                <span className="config-label">
                  Available Credit
                  <Tooltip title="当前可用信用额度（单位：元），用于生成推荐方案">
                    <InfoCircleOutlined className="label-info" />
                  </Tooltip>
                </span>
                <input type="number" min={0} step={1} value={config.availableCredit}
                  onChange={(e) => setConfig({ ...config, availableCredit: e.target.value })}
                  className="config-input" />
              </div>
              <div className="config-row">
                <span className="config-label">
                  Current Loan Plan
                  <Tooltip title="当前已选贷款方案（可为空），仅用于上下文展示">
                    <InfoCircleOutlined className="label-info" />
                  </Tooltip>
                </span>
                <input type="text" value={config.currentLoanPlan}
                  onChange={(e) => setConfig({ ...config, currentLoanPlan: e.target.value })}
                  className="config-input" />
              </div>
              <div className="config-row">
                <span className="config-label">
                  Recent Repayment Status
                  <Tooltip title="最近一期还款状态，用于生成智能回复">
                    <InfoCircleOutlined className="label-info" />
                  </Tooltip>
                </span>
                <select
                  value={config.recentRepaymentStatus}
                  onChange={(e) => setConfig({ ...config, recentRepaymentStatus: e.target.value })}
                  className="config-input"
                >
                  {REPAYMENT_STATUS_OPTIONS.map((opt) => (
                    <option key={opt} value={opt}>{opt}</option>
                  ))}
                </select>
              </div>
              <div className="config-row">
                <span className="config-label">
                  Max Loan Amount
                  <Tooltip title="系统允许的最高可贷额度（单位：元），用于推荐方案上限">
                    <InfoCircleOutlined className="label-info" />
                  </Tooltip>
                </span>
                <input type="number" min={0} step={1} value={config.maxLoanAmount}
                  onChange={(e) => setConfig({ ...config, maxLoanAmount: e.target.value })}
                  className="config-input" />
              </div>
              <div className="config-row">
                <span className="config-label">
                  授信状态
                  <Tooltip title="当前是否已完成授信：已授信将直接展示借款方案，未授信进入授信流程">
                    <InfoCircleOutlined className="label-info" />
                  </Tooltip>
                </span>
                <select
                  value={config.authorizationStatus}
                  onChange={(e) => {
                    const val = e.target.value;
                    setConfig({ ...config, authorizationStatus: val });
                    // 不再调用后端授权更新接口；该状态仅作为聊天上下文参数传递给后端
                    // message.info('已更新授信状态：该设置会在聊天请求中传递给后端');
                  }}
                  className="config-input"
                >
                  {AUTH_STATUS_OPTIONS.map((opt) => (
                    <option key={opt} value={opt}>{opt}</option>
                  ))}
                </select>
              </div>
            </div>
            <div className="config-actions">
              <Button onClick={handleResetConfig}>Reset</Button>
            </div>
          </Card>
        </div>

        {/* 右侧主区域：对话窗口 */}
        <div className="main">
          <ChatWindow
            messages={messages}
            onSend={handleChatSend}
            stepData={stepData}
            onComplete={handleStepComplete}
            loading={loading}
            onRollback={handleRollback}
            onRestart={handleRestart}
            canRollback={canRollback}
            onStartCredit={handleStartCredit}
            onAssistantReply={null}
          />
          {/* 纯聊天窗口模式：隐藏进度条 */}
          {false && (
            <div className="progress-container">
              <Steps current={currentStep} size="small">
                {Array.from({ length: 8 }, (_, i) => (
                  <Step key={i} title={getStepTitle(i)} />
                ))}
              </Steps>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default App;