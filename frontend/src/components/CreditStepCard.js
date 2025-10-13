import React, { useState } from 'react';
import { Card, Button, Form, Input, Upload, Select, message, Skeleton } from 'antd';
import { UploadOutlined } from '@ant-design/icons';

const { Option } = Select;

const CreditStepCard = ({ stepData, onComplete, loading }) => {
  // 总是创建表单实例，但只在需要时渲染表单
  const [form] = Form.useForm();
  // 兼容后端返回的 cardId 或 id 字段
  const key = stepData?.cardId || stepData?.id;
  // 借款方案的可编辑选择项（下拉）
  const [loanTerm, setLoanTerm] = useState(null);
  const [accountSelection, setAccountSelection] = useState(null);
  const [loanPurpose, setLoanPurpose] = useState(null);

  const renderStepContent = () => {
    switch (key) {
      case 'live_detect':
        return (
          <div className="step-content">
            <h3>🎯 第一步：活体检测</h3>
            <p>请确保光线充足，正对摄像头进行人脸识别</p>
            <div style={{ textAlign: 'center', margin: '24px 0' }}>
              <div style={{ 
                width: 200, 
                height: 200, 
                background: '#f0f0f0', 
                borderRadius: 8,
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 48,
                color: '#999'
              }}>
                📷
              </div>
            </div>
            <p style={{ color: '#666', fontSize: 14 }}>
              点击"开始检测"按钮，系统将启动摄像头进行人脸识别
            </p>
          </div>
        );

      case 'id_upload':
        return (
          <div className="step-content">
            <h3>📄 第二步：上传身份证</h3>
            <Form form={form} layout="vertical">
              <Form.Item
                label="身份证正面"
                name="idFront"
              >
                <Upload
                  maxCount={1}
                  accept="image/*"
                  beforeUpload={() => false}
                >
                  <Button icon={<UploadOutlined />}>上传身份证正面</Button>
                </Upload>
              </Form.Item>
              <Form.Item
                label="身份证反面"
                name="idBack"
              >
                <Upload
                  maxCount={1}
                  accept="image/*"
                  beforeUpload={() => false}
                >
                  <Button icon={<UploadOutlined />}>上传身份证反面</Button>
                </Upload>
              </Form.Item>
            </Form>
          </div>
        );

      case 'set_password':
        return (
          <div className="step-content">
            <h3>🔐 第三步：设置交易密码</h3>
            <Form form={form} layout="vertical">
              <Form.Item
                label="交易密码"
                name="password"
              >
                <Input.Password 
                  placeholder="请输入6位数字密码"
                  maxLength={6}
                />
              </Form.Item>
              <Form.Item
                label="确认密码"
                name="confirmPassword"
              >
                <Input.Password 
                  placeholder="请再次输入密码"
                  maxLength={6}
                />
              </Form.Item>
            </Form>
          </div>
        );

      case 'sign_agreement':
        return (
          <div className="step-content">
            <h3>📋 第四步：签署授信协议</h3>
            <div style={{ 
              background: '#f6ffed', 
              border: '1px solid #b7eb8f', 
              borderRadius: 6,
              padding: 16,
              marginBottom: 16
            }}>
              <h4>《消费贷授信协议》</h4>
              <div style={{ maxHeight: 200, overflow: 'auto', marginTop: 12 }}>
                <p>1. 授信额度：最高20万元</p>
                <p>2. 年化利率：7.2% - 18%（根据信用评估）</p>
                <p>3. 还款期限：6-36个月</p>
                <p>4. 提前还款：支持随时提前还款，无违约金</p>
                <p>5. 逾期处理：按日收取逾期利息</p>
                <p>6. 个人信息使用：仅用于授信评估和风险管理</p>
              </div>
            </div>
            <Form form={form}>
              <Form.Item
                name="agreement"
                valuePropName="checked"
                rules={[{ validator: (_, value) => value ? Promise.resolve() : Promise.reject(new Error('请同意协议')) }]}
              >
                <input type="checkbox" id="agreement" />
                <label htmlFor="agreement" style={{ marginLeft: 8 }}>
                  我已阅读并同意《消费贷授信协议》
                </label>
              </Form.Item>
            </Form>
          </div>
        );

      case 'bind_card':
        return (
          <div className="step-content">
            <h3>💳 第五步：绑定银行卡</h3>
            <Form form={form} layout="vertical">
              <Form.Item
                label="银行卡号"
                name="cardNumber"
              >
                <Input placeholder="请输入银行卡号" maxLength={19} />
              </Form.Item>
              <Form.Item
                label="开户银行"
                name="bankName"
              >
                <Select placeholder="请选择开户银行">
                  <Option value="ICBC">中国工商银行</Option>
                  <Option value="ABC">中国农业银行</Option>
                  <Option value="BOC">中国银行</Option>
                  <Option value="CCB">中国建设银行</Option>
                  <Option value="BOCOM">交通银行</Option>
                  <Option value="CMB">招商银行</Option>
                  <Option value="SPDB">浦发银行</Option>
                  <Option value="CIB">兴业银行</Option>
                </Select>
              </Form.Item>
              <Form.Item
                label="预留手机号"
                name="phone"
              >
                <Input placeholder="请输入银行预留手机号" maxLength={11} />
              </Form.Item>
            </Form>
          </div>
        );

      case 'occupation':
        return (
          <div className="step-content">
            <h3>💼 第六步：填写职业信息</h3>
            <Form form={form} layout="vertical">
              <Form.Item
                label="职业类型"
                name="occupationType"
              >
                <Select placeholder="请选择职业类型">
                  <Option value="employee">上班族</Option>
                  <Option value="business">个体户</Option>
                  <Option value="freelance">自由职业</Option>
                  <Option value="student">学生</Option>
                  <Option value="other">其他</Option>
                </Select>
              </Form.Item>
              <Form.Item
                label="单位名称"
                name="companyName"
              >
                <Input placeholder="请输入单位名称" />
              </Form.Item>
              <Form.Item
                label="月收入（元）"
                name="monthlyIncome"
              >
                <Select placeholder="请选择月收入">
                  <Option value="3000-5000">3000-5000</Option>
                  <Option value="5000-8000">5000-8000</Option>
                  <Option value="8000-12000">8000-12000</Option>
                  <Option value="12000-20000">12000-20000</Option>
                  <Option value="20000+">20000以上</Option>
                </Select>
              </Form.Item>
            </Form>
          </div>
        );

      case 'contact':
        return (
          <div className="step-content">
            <h3>👥 第七步：填写联系人</h3>
            <Form form={form} layout="vertical">
              <div style={{ marginBottom: 24 }}>
                <h4>联系人1（必填）</h4>
                <Form.Item
                  label="姓名"
                  name={['contact1', 'name']}
                >
                  <Input placeholder="请输入联系人姓名" />
                </Form.Item>
                <Form.Item
                  label="手机号"
                  name={['contact1', 'phone']}
                >
                  <Input placeholder="请输入联系人手机号" maxLength={11} />
                </Form.Item>
                <Form.Item
                  label="关系"
                  name={['contact1', 'relation']}
                >
                  <Select placeholder="请选择关系">
                    <Option value="family">家人</Option>
                    <Option value="friend">朋友</Option>
                    <Option value="colleague">同事</Option>
                  </Select>
                </Form.Item>
              </div>
              <div>
                <h4>联系人2（必填）</h4>
                <Form.Item
                  label="姓名"
                  name={['contact2', 'name']}
                >
                  <Input placeholder="请输入联系人姓名" />
                </Form.Item>
                <Form.Item
                  label="手机号"
                  name={['contact2', 'phone']}
                >
                  <Input placeholder="请输入联系人手机号" maxLength={11} />
                </Form.Item>
                <Form.Item
                  label="关系"
                  name={['contact2', 'relation']}
                >
                  <Select placeholder="请选择关系">
                    <Option value="family">家人</Option>
                    <Option value="friend">朋友</Option>
                    <Option value="colleague">同事</Option>
                  </Select>
                </Form.Item>
              </div>
            </Form>
          </div>
        );

      case 'assess_wait':
        return (
          <div className="step-content">
            <h3>⏳ 第八步：系统评估中</h3>
            <div style={{ textAlign: 'center', margin: '48px 0' }}>
              <div style={{ fontSize: 64, marginBottom: 24 }}>🤖</div>
              <h4>系统正在评估您的授信申请</h4>
              <p style={{ color: '#666', marginTop: 16 }}>
                请稍等片刻，评估过程大约需要30秒...
              </p>
              <div style={{ 
                width: '80%', 
                height: 4, 
                background: '#f0f0f0', 
                borderRadius: 2,
                margin: '24px auto',
                overflow: 'hidden'
              }}>
                <div style={{
                  width: '60%',
                  height: '100%',
                  background: '#1890ff',
                  borderRadius: 2,
                  animation: 'loading 2s ease-in-out infinite'
                }}></div>
              </div>
            </div>
          </div>
        );

      case 'consumer_loan_offers':
        const isMock = !(stepData && (stepData.offer || stepData.payload?.offer));
        const offer = (stepData && (stepData.offer || stepData.payload?.offer)) || {
          amount: 48000,
          annualRate: 14.4,
          termMonths: 12,
          repayMode: '每期等本',
          firstPayment: 4614.40,
          totalInterest: 3832.00,
          bankName: '网商银行',
          bankTail: '5386',
          lender: '福建海峡银行',
          discountText: '查看优惠',
          purpose: '消费购物'
        };
        const termVal = loanTerm ?? offer.termMonths;
        const accountVal = accountSelection ?? `${offer.bankName}(${offer.bankTail})`;
        const purposeVal = loanPurpose ?? offer.purpose;
        return (
          <div className="step-content">
            {/* 标题弱化：取消大标题样式 */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
              <div style={{ fontSize: 16, color: '#1677ff' }}>为您推荐以下消费贷方案</div>
            </div>

            <Skeleton active loading={loading}>
              {/* 金额与利率 */}
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
                <div style={{ fontSize: 28, fontWeight: 700, color: '#000' }}>¥{offer.amount.toLocaleString()}</div>
                <div style={{ color: '#666' }}>年利率(单利) {offer.annualRate}%</div>
              </div>

              {/* 信息区 */}
              <div style={{ background: '#fafafa', border: '1px solid #f0f0f0', borderRadius: 8, padding: 12, marginTop: 12 }}>
                {/* 享优惠 */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ color: '#666' }}>享优惠</span>
                  <Button type="link" style={{ padding: 0 }} onClick={() => message.info('您当前享受限时优惠，具体利率与费用以放款页面为准')}>{offer.discountText}</Button>
                </div>

                {/* 借多久：下拉可编辑 */}
                <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 12 }}>
                  <span style={{ color: '#666' }}>借多久</span>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                    <Select
                      size="small"
                      value={termVal}
                      onChange={(v) => setLoanTerm(v)}
                      options={[6, 9, 12, 18, 24, 36].map((m) => ({ value: m, label: `${m}个月` }))}
                      style={{ minWidth: 96 }}
                    />
                    <span>随时可还</span>
                  </span>
                </div>

                {/* 怎么还 */}
                <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 12 }}>
                  <span style={{ color: '#666' }}>怎么还</span>
                  <span>
                    {offer.repayMode} 首期还{offer.firstPayment.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                    <span style={{ color: '#999', marginLeft: 8 }}>总利息{offer.totalInterest.toLocaleString(undefined, { minimumFractionDigits: 2 })}</span>
                  </span>
                </div>

                {/* 收款账户：下拉可编辑 */}
                <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 12 }}>
                  <span style={{ color: '#666' }}>收款账户</span>
                  <span style={{ display: 'inline-flex', alignItems: 'center' }}>
                    <Select
                      size="small"
                      value={accountVal}
                      onChange={(v) => setAccountSelection(v)}
                      options={[
                        '网商银行(5386)',
                        '中国银行(1234)',
                        '招商银行(4321)',
                        '建设银行(5566)',
                        '工商银行(9988)'
                      ].map((s) => ({ value: s, label: s }))}
                      style={{ minWidth: 156 }}
                    />
                  </span>
                </div>
                <div style={{ color: '#999', fontSize: 12, marginTop: 4 }}>还款日将优先此账户自动扣款</div>

                {/* 借款用途：下拉可编辑 */}
                <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 12 }}>
                  <span style={{ color: '#666' }}>借款用途</span>
                  <span>
                    <Select
                      size="small"
                      value={purposeVal}
                      onChange={(v) => setLoanPurpose(v)}
                      options={['消费购物','教育培训','家装','医美','旅游','租房','其他'].map((s) => ({ value: s, label: s }))}
                      style={{ minWidth: 120 }}
                    />
                  </span>
                </div>

                {/* 出资机构 */}
                <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 12 }}>
                  <span style={{ color: '#666' }}>出资机构</span>
                  <span>{offer.lender}</span>
                </div>
              </div>

              {isMock && (
                <div style={{ color: '#999', fontSize: 12, marginTop: 8 }}>当前为示例方案，仅用于预览。</div>
              )}
            </Skeleton>
          </div>
        );

      default:
        return (
          <div className="step-content">
            <h3>{stepData?.title || '未知步骤'}</h3>
            <p>{stepData?.content || '正在加载步骤信息...'}</p>
          </div>
        );
    }
  };

  const handleComplete = async () => {
    try {
      const key = stepData?.cardId || stepData?.id;
      // 评估等待步骤不允许点击，避免误触导致流程回退
      if (key === 'assess_wait') {
        return; // 直接忽略点击
      }
      if (key === 'live_detect' || key === 'sign_agreement' || key === 'consumer_loan_offers' || key === 'id_upload' || key === 'set_password' || key === 'bind_card' || key === 'occupation' || key === 'contact') {
        // 不需要表单验证的步骤；若为借款方案，传递用户选择项
        if (key === 'consumer_loan_offers') {
          const rawOffer = (stepData && (stepData.offer || stepData.payload?.offer)) || {};
          const payload = {
            termMonths: loanTerm ?? rawOffer.termMonths,
            account: accountSelection ?? (rawOffer.bankName && rawOffer.bankTail ? `${rawOffer.bankName}(${rawOffer.bankTail})` : undefined),
            purpose: loanPurpose ?? rawOffer.purpose
          };
          onComplete(payload);
        } else {
          onComplete();
        }
      } else {
        // 需要表单验证的步骤
        if (form) {
          await form.validateFields();
          const formData = form.getFieldsValue();
          console.log('表单数据:', formData);
          onComplete(formData);
        } else {
          // 如果没有表单实例，直接调用onComplete
          onComplete();
        }
      }
    } catch (error) {
      console.error('表单验证失败:', error);
      message.error('请完善必填信息');
    }
  };

  return (
    <Card className="step-card">
      {renderStepContent()}
      <div className="action-buttons" style={{ display: 'flex', justifyContent: 'center' }}>
        <Button 
          type="primary" 
          size="large" 
          onClick={handleComplete}
          loading={loading}
          disabled={key === 'assess_wait'}
          style={{ minWidth: 160 }}
        >
          {key === 'assess_wait' ? '等待评估完成' : (key === 'consumer_loan_offers' ? '同意协议并借钱' : '下一步')}
        </Button>
      </div>
    </Card>
  );
};

export default CreditStepCard;