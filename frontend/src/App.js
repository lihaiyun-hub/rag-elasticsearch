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
  const [error] = useState(null);
  const [messages, setMessages] = useState([]);
  const [stepHistory, setStepHistory] = useState([]);

  // 请求配置：用户上下文与流程起始步骤
  const START_STEP_OPTIONS = Array.from({ length: 8 }, (_, i) => i + 1);
  const REPAYMENT_STATUS_OPTIONS = ['正常', '逾期', '已结清'];
  const AUTH_STATUS_OPTIONS = ['未授信', '已授信'];
  const defaultConfig = {
    startStep: 1,
    userName: 'John Doe',
    availableCredit: '',
    // currentLoanPlan: '无', // 已移除
    recentRepaymentStatus: '正常',
    authorizationStatus: '未授信',
    // 新增可配置项默认值
    termOptions: '6,12,24,36',
    loanPurposes: '日常消费,教育培训,医疗健康,家庭装修,旅游出行,数码家电,其他',
    bankCardNumber: '6222020000006666',
    bankName: '招商银行'
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

  // 未授信时清空可用额度并在UI中禁用输入
  useEffect(() => {
    // 根据授权状态自动调整额度：未授信清空；已授信且为空则设置默认值
    setConfig(prev => {
      if (config.authorizationStatus !== '已授信' && prev.availableCredit !== '') {
        return { ...prev, availableCredit: '' };
      }
      if (config.authorizationStatus === '已授信' && (prev.availableCredit === '' || prev.availableCredit == null)) {
        return { ...prev, availableCredit: '5000' };
      }
      return prev;
    });
  }, [config.authorizationStatus]);

  const addAssistantMessage = (content, card = null) => {
    setMessages((prev) => [...prev, { role: 'assistant', content, ...(card ? { card } : {}) }]);
  };
  const addUserMessage = (content) => {
    setMessages((prev) => [...prev, { role: 'user', content }]);
  };


  // 初始化授信流程方法已移除（原仅由欢迎卡片触发）

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
        // 将下一步卡片与说明合并为单条助手消息
        const tipText = `已完成【${getCardName(currKey)}】。即将进入【${getCardName(nextKey)}】。${getCardTip(nextKey)}`;
        // 检查是否完成
        if (nextCard.state === 'COMPLETED' || nextCard.state === 'CREDIT_DONE' || nextKey === 'consumer_loan_offers') {
          setAuthorized(true);
          setStepData(nextCard);
          setCurrentStep(0);
          setStepHistory([nextCard]);
          const doneText = '授信已完成，以下为为您推荐的借款方案';
          addAssistantMessage(doneText, nextCard);
        } else {
          // 进入下一步
          setStepData(nextCard);
          setCurrentStep((s) => s + 1);
          setStepHistory((h) => [...h, nextCard]);
          addAssistantMessage(tipText, nextCard);
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
      // 解析 termOptions（支持逗号分隔字符串或数组）
      const parsedTermOptions = typeof config.termOptions === 'string'
        ? config.termOptions.split(',').map(s => parseInt(s.trim(), 10)).filter(n => Number.isFinite(n) && n > 0)
        : (Array.isArray(config.termOptions) ? config.termOptions : undefined);
      // 解析 loanPurposes（支持逗号分隔字符串或数组）
      const parsedLoanPurposes = typeof config.loanPurposes === 'string'
        ? config.loanPurposes.split(',').map(s => s.trim()).filter(s => s.length > 0)
        : (Array.isArray(config.loanPurposes) ? config.loanPurposes : undefined);
      const reply = await assistantAPI.chat(chatId, text, {
        userName: config.userName || undefined,
        // 仅在已授信时传递可用额度
        availableCredit: (config.authorizationStatus === '已授信' && config.availableCredit !== '') ? Number(config.availableCredit) : undefined,
        // currentLoanPlan: config.currentLoanPlan || undefined, // 已移除
        recentRepaymentStatus: config.recentRepaymentStatus || undefined,
        authorized: config.authorizationStatus === '已授信',
        // 可选分期数：若在 config.termOptions 中提供（数组），则传递给后端用于方案期数选择
        termOptions: (parsedTermOptions && parsedTermOptions.length > 0) ? parsedTermOptions : undefined,
        // 新增：用途选项传递到后端用于系统提示词注入
        loanPurposes: (parsedLoanPurposes && parsedLoanPurposes.length > 0) ? parsedLoanPurposes : undefined,
        // 新增：银行卡号与银行名
        bankCardNumber: (config.bankCardNumber && config.bankCardNumber.trim() !== '') ? config.bankCardNumber.trim() : undefined,
        bankName: (config.bankName && config.bankName.trim() !== '') ? config.bankName.trim() : undefined
      });
      // 适配结构化返回：优先渲染文本，其次根据card决定是否渲染步骤卡片或切换到授信完成页
      if (reply && typeof reply === 'object') {
        const { text: botText, card } = reply;
        if (card) {
          const key = getCardKey(card);
          // 忽略纯文本/错误卡片
          if (key && key !== 'text_response' && key !== 'error') {
            setStepData(card);
            setCurrentStep(0);
            setStepHistory([card]);
            if (key === 'consumer_loan_offers' || card.state === 'CREDIT_DONE' || card.state === 'COMPLETED') {
              setAuthorized(true);
              const textMsg = botText || '授信已完成，以下为为您推荐的借款方案';
              addAssistantMessage(textMsg, card);
            } else {
              const textMsg = botText || `${getCardName(key)}开始。${getCardTip(key)}`;
              addAssistantMessage(textMsg, card);
            }
          } else if (botText) {
            addAssistantMessage(botText);
          } else {
            // 结构化响应但无可展示文本/卡片时的兜底文案
            addAssistantMessage('我已经收到你的请求，但暂时没有可展示的回复。试试换个问法或提供更多信息。');
          }
        } else if (botText) {
          addAssistantMessage(botText);
        } else {
          // 对象响应但无文本且无卡片时的兜底
          addAssistantMessage('我暂时没有准备好回答这个问题，请稍后再试。');
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
    addAssistantMessage(`已返回到【${getCardName(prevKey)}】。${getCardTip(prevKey)}`, prevCard);
  };

  const canRollback = stepHistory.length > 1 && !loading && !authorized;

  // 已移除通过欢迎卡片触发的“开始授信流程”入口

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
                  <Tooltip title="仅在已授信状态下可填写的可用额度（单位：元）">
                    <InfoCircleOutlined className="label-info" />
                  </Tooltip>
                </span>
                <input
                  type="number"
                  min={0}
                  step={1}
                  value={config.availableCredit}
                  onChange={(e) => setConfig({ ...config, availableCredit: e.target.value })}
                  className="config-input"
                  placeholder={config.authorizationStatus === '已授信' ? '请输入额度，如 5000' : '未授信不可用'}
                  disabled={config.authorizationStatus !== '已授信'}
                />
              </div>
              {/* 已移除 currentLoanPlan 输入行 */}
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
                  Authorization Status
                  <Tooltip title="是否已完成授信，仅用于上下文展示">
                    <InfoCircleOutlined className="label-info" />
                  </Tooltip>
                </span>
                <select
                  value={config.authorizationStatus}
                  onChange={(e) => setConfig({ ...config, authorizationStatus: e.target.value })}
                  className="config-input"
                >
                  {AUTH_STATUS_OPTIONS.map((opt) => (
                    <option key={opt} value={opt}>{opt}</option>
                  ))}
                </select>
              </div>
              {/* 新增配置项：分期、银行名、银行卡号 */}
              <div className="config-row">
                <span className="config-label">
                  Term Options
                  <Tooltip title="可选分期（以逗号分隔，如 6,12,24,36）">
                    <InfoCircleOutlined className="label-info" />
                  </Tooltip>
                </span>
                <input type="text" value={config.termOptions}
                  onChange={(e) => setConfig({ ...config, termOptions: e.target.value })}
                  className="config-input" />
              </div>
              <div className="config-row">
                <span className="config-label">
                  Loan Purposes
                  <Tooltip title="借款用途选项（以逗号分隔，如 日常消费,教育培训,医疗健康）">
                    <InfoCircleOutlined className="label-info" />
                  </Tooltip>
                </span>
                <input type="text" value={config.loanPurposes}
                  onChange={(e) => setConfig({ ...config, loanPurposes: e.target.value })}
                  className="config-input" />
              </div>
              <div className="config-row">
                <span className="config-label">
                  Bank Name
                  <Tooltip title="银行名称，如 招商银行、中国银行">
                    <InfoCircleOutlined className="label-info" />
                  </Tooltip>
                </span>
                <input type="text" value={config.bankName}
                  onChange={(e) => setConfig({ ...config, bankName: e.target.value })}
                  className="config-input" />
              </div>
              <div className="config-row">
                <span className="config-label">
                  Bank Card Number
                  <Tooltip title="银行卡号仅用于计算尾号展示，完整号码不会在界面显示或传输给未授权模块">
                    <InfoCircleOutlined className="label-info" />
                  </Tooltip>
                </span>
                <input type="text" value={config.bankCardNumber}
                  onChange={(e) => setConfig({ ...config, bankCardNumber: e.target.value })}
                  className="config-input" />
              </div>
              <div className="config-actions">
                <Button onClick={handleResetConfig} disabled={loading}>
                  重置配置
                </Button>
              </div>
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