import axios from 'axios';

const API_BASE_URL = 'http://127.0.0.1:8082/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  },
  withCredentials: false,
  mode: 'cors',
});

// 请求拦截器
api.interceptors.request.use(
  (config) => {
    console.log('API Request:', config.method.toUpperCase(), config.url, config.data);
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 响应拦截器
api.interceptors.response.use(
  (response) => {
    console.log('API Response:', response.status, response.data);
    // 将后端的ChatResponse格式转换为前端期望的格式
    if (response.data) {
      const originalData = response.data;
      
      // 处理ChatResponse格式
      if (originalData.type === 'card_list' && Array.isArray(originalData.payload)) {
        const cards = originalData.payload;
        if (cards.length > 0) {
          // 转换格式：{ type: 'card_list', payload: [...] } -> { data: { card: {...} } }
          response.data = {
            data: {
              card: {
                ...cards[0],
                state: originalData.state,
                trackingId: originalData.trackingId
              }
            }
          };
          console.log('转换card_list响应格式:', response.data);
        }
      } else if (originalData.type === 'text' && originalData.payload) {
        // 处理文本响应
        response.data = {
          data: {
            card: {
              id: 'text_response',
              content: originalData.payload,
              state: originalData.state,
              trackingId: originalData.trackingId
            }
          }
        };
        console.log('转换text响应格式:', response.data);
      } else if (originalData.type === 'error') {
        // 处理错误响应
        response.data = {
          data: {
            card: {
              id: 'error',
              content: originalData.payload || '处理失败',
              state: originalData.state,
              trackingId: originalData.trackingId
            }
          }
        };
        console.log('转换error响应格式:', response.data);
      }
    }
    return response;
  },
  (error) => {
    console.error('API Error:', error.response?.status, error.response?.data);
    return Promise.reject(error);
  }
);

// 消费贷相关API
export const consumerCreditAPI = {
  // 获取授信状态
  getStatus: (chatId) => api.get(`/consumer-credit/status?chatId=${chatId}`),
  
  // 完成步骤
  completeStep: async (chatId, step) => {
    const response = await api.post('/consumer-credit/step', { chatId, step });
    return response.data;
  },
  
  // 初始化授信流程（允许自定义起始步骤）
  startCreditProcess: async (chatId, startStep = 1) => {
    console.log('startCreditProcess called with chatId:', chatId, 'startStep:', startStep);
    console.log('Request URL:', `${API_BASE_URL}/consumer-credit/step`);
    console.log('Request config:', {
      headers: { 'Content-Type': 'application/json' },
      withCredentials: false,
      timeout: 30000
    });
    try {
      const response = await api.post('/consumer-credit/step', { chatId, step: startStep }, {
        headers: { 'Content-Type': 'application/json' },
        withCredentials: false,
        timeout: 30000
      });
      console.log('Response received:', response);
      return response.data;
    } catch (error) {
      console.error('Request failed:', error);
      console.error('Error config:', error.config);
      console.error('Error response:', error.response);
      console.error('Error request:', error.request);
      throw error;
    }
  },

};

// 助手通用聊天API
export const assistantAPI = {
  /**
   * 与后端助手聊天
   * @param {string} chatId - 会话ID
   * @param {string} userMessage - 用户输入
   * @param {object} [context] - 可选的用户上下文
   * @returns {Promise<{text: string, card: object|null, raw: any}>} 助手回复结构化结果
   */
  chat: async (chatId, userMessage, context = {}) => {
    try {
      const body = {
        chatId,
        userMessage,
      };
      if (context) {
        const { userName, availableCredit, recentRepaymentStatus, authorized, termOptions, loanPurposes, bankCardNumber, bankName } = context;
        if (userName) body.userName = userName;
        if (availableCredit != null) body.availableCredit = availableCredit;
        if (recentRepaymentStatus) body.recentRepaymentStatus = recentRepaymentStatus;
        if (authorized != null) body.authorized = authorized;
        if (Array.isArray(termOptions) && termOptions.length > 0) body.termOptions = termOptions;
        if (Array.isArray(loanPurposes) && loanPurposes.length > 0) body.loanPurposes = loanPurposes;
        // 新增：银行卡后四位与银行名传递到后端
        if (typeof bankCardNumber === 'string' && bankCardNumber.trim() !== '') body.bankCardNumber = bankCardNumber.trim();
        if (typeof bankName === 'string' && bankName.trim() !== '') body.bankName = bankName.trim();
      }
      const response = await api.post('/assistant/chat', body);
      const data = response.data;
      let textReply = '';
      let cardObj = null;
      if (typeof data === 'string') {
        textReply = data;
      } else if (data && typeof data === 'object') {
        const card = data?.data?.card;
        if (card) {
          if (card.payload && typeof card.payload.offer === 'string') {
            try {
              const parsed = JSON.parse(card.payload.offer);
              card.offer = parsed;
              card.payload.offer = parsed;
            } catch (e) {
              console.warn('解析 payload.offer 失败:', e);
            }
          }
          cardObj = card;
        }
        if (card && (card.content || card.text)) {
          textReply = card.content || card.text;
        } else if (data.payload && typeof data.payload === 'string') {
          textReply = data.payload;
        } else if (data.message && typeof data.message === 'string') {
          textReply = data.message;
        } else {
          textReply = '';
        }
      } else {
        textReply = '';
      }
      return { text: textReply, card: cardObj, raw: data };
    } catch (error) {
      console.error('Assistant chat failed:', error);
      throw error;
    }
  },
};

export default api;