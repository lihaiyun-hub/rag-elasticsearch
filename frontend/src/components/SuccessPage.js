import React from 'react';
import { Card, Button, Result, List, Tag, Progress } from 'antd';
import { CheckCircleOutlined, CreditCardOutlined, ClockCircleOutlined } from '@ant-design/icons';

const SuccessPage = ({ offers, onSelectOffer, chatId }) => {
  const formatAmount = (amount) => {
    return (amount / 10000).toFixed(1) + '万元';
  };

  const formatRate = (rate) => {
    return rate.toFixed(1) + '%';
  };

  const offersData = offers || [
    {
      id: 1,
      amount: 50000,
      rate: 7.2,
      term: 12,
      monthlyPayment: 4333,
      totalInterest: 1996,
      tag: '推荐'
    },
    {
      id: 2,
      amount: 80000,
      rate: 8.5,
      term: 24,
      monthlyPayment: 3667,
      totalInterest: 8008,
      tag: '灵活'
    },
    {
      id: 3,
      amount: 100000,
      rate: 9.8,
      term: 36,
      monthlyPayment: 3222,
      totalInterest: 16000,
      tag: '长期'
    }
  ];

  return (
    <div className="success-container">
      <Result
        status="success"
        title="授信申请已通过！"
        subTitle="恭喜您获得消费贷授信资格，请选择适合您的借款方案"
        extra={[
          <div key="credit-info" style={{ marginTop: 24 }}>
            <Card style={{ marginBottom: 24 }}>
              <div style={{ display: 'flex', justifyContent: 'space-around', textAlign: 'center' }}>
                <div>
                  <div style={{ fontSize: 24, fontWeight: 'bold', color: '#1890ff' }}>10万</div>
                  <div style={{ color: '#666' }}>最高可借</div>
                </div>
                <div>
                  <div style={{ fontSize: 24, fontWeight: 'bold', color: '#52c41a' }}>7.2%</div>
                  <div style={{ color: '#666' }}>最低年利率</div>
                </div>
                <div>
                  <div style={{ fontSize: 24, fontWeight: 'bold', color: '#722ed1' }}>36期</div>
                  <div style={{ color: '#666' }}>最长分期</div>
                </div>
              </div>
            </Card>
          </div>
        ]}
      />

      <Card title="💰 为您推荐的借款方案" className="loan-offers">
        <List
          itemLayout="vertical"
          dataSource={offersData}
          renderItem={(offer) => (
            <List.Item key={offer.id}>
              <Card 
                className="offer-card" 
                actions={[
                  <Button 
                    type="primary" 
                    onClick={() => onSelectOffer(offer)}
                    style={{ width: '100%' }}
                  >
                    选择此方案
                  </Button>
                ]}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <div style={{ fontSize: 24, fontWeight: 'bold', color: '#1890ff' }}>
                      {formatAmount(offer.amount)}
                    </div>
                    <div style={{ color: '#666' }}>借款金额</div>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <Tag color="blue">{offer.tag}</Tag>
                    <div style={{ fontSize: 18, fontWeight: 'bold', color: '#52c41a' }}>
                      {formatRate(offer.rate)}
                    </div>
                    <div style={{ color: '#666' }}>年利率</div>
                  </div>
                </div>
                
                <div style={{ marginTop: 16, padding: '16px 0', borderTop: '1px solid #f0f0f0' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                    <span style={{ color: '#666' }}>借款期限：</span>
                    <span>{offer.term}个月</span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                    <span style={{ color: '#666' }}>月供金额：</span>
                    <span style={{ fontWeight: 'bold' }}>¥{offer.monthlyPayment.toLocaleString()}</span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span style={{ color: '#666' }}>总利息：</span>
                    <span>¥{offer.totalInterest.toLocaleString()}</span>
                  </div>
                </div>

                <div style={{ marginTop: 16 }}>
                  <div style={{ marginBottom: 8 }}>
                    <span style={{ color: '#666' }}>还款进度：</span>
                  </div>
                  <Progress 
                    percent={0} 
                    showInfo={false}
                    strokeColor="#1890ff"
                  />
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: '#999' }}>
                    <span>第1期</span>
                    <span>第{offer.term}期</span>
                  </div>
                </div>
              </Card>
            </List.Item>
          )}
        />
      </Card>

      <Card style={{ marginTop: 24 }}>
        <div style={{ textAlign: 'center' }}>
          <p style={{ color: '#666', marginBottom: 16 }}>
            💡 温馨提示：选择合适的借款方案，理性消费，按时还款
          </p>
          <div style={{ display: 'flex', justifyContent: 'center', gap: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center', color: '#52c41a' }}>
              <CheckCircleOutlined style={{ marginRight: 4 }} />
              随借随还
            </div>
            <div style={{ display: 'flex', alignItems: 'center', color: '#1890ff' }}>
              <ClockCircleOutlined style={{ marginRight: 4 }} />
              快速到账
            </div>
            <div style={{ display: 'flex', alignItems: 'center', color: '#722ed1' }}>
              <CreditCardOutlined style={{ marginRight: 4 }} />
              灵活分期
            </div>
          </div>
        </div>
      </Card>
    </div>
  );
};

export default SuccessPage;