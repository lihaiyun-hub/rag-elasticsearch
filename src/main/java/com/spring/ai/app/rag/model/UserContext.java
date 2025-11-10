package com.spring.ai.app.rag.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * @author LHY
 * @date 2025-10-30 16:57
 * @description
 */
@Data
@AllArgsConstructor
public class UserContext {

    private String userName;
    private Double availableCredit;
    private Boolean authorized; // 授信状态：null 表示未提供；true/false 表示显式状态
    private List<Integer> termOptions;
    private String loanPurposeCode;
    private String bankCardNumber;
    private String bankName;

    private String tenantCode; // 租户编码
    private String workFlowFlag;  // 授信/借款流程标识
    private String workFlowCode;  // 授信/借款流程编码
    private Integer messageType;  // 消息处理类型
    private String userId;  // 用户ID
    private String sessionId;  // 会话ID
    private String uuid;  // 唯一标识

    // Flattened from Profile
    private String gender;
    private String currentPhone;
    private String registerPhone;

    // Flattened from LoanInfo
    private String contractNum;
    private String contractStatus; // 合同状态
    private String price;          // 金额（messageType=5兼容字段）
    private String bankCarCode;    // 银行卡编号
    private String term;           // 分期期数（messageType=5兼容字段）

    public UserContext() {
        // 默认值
        this.userName = "尊敬的客户";
        this.availableCredit = 10000.0;
        this.authorized = null;
    }

    public UserContext(String userName, Double availableCredit) {
        this.userName = userName;
        this.availableCredit = availableCredit;
        this.authorized = null;
    }

    // Getters and Setters
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Double getAvailableCredit() {
        return availableCredit;
    }

    public void setAvailableCredit(Double availableCredit) {
        this.availableCredit = availableCredit;
    }

    public Boolean getAuthorized() {
        return authorized;
    }

    public void setAuthorized(Boolean authorized) {
        this.authorized = authorized;
    }

    public List<Integer> getTermOptions() {
        return termOptions;
    }

    public void setTermOptions(List<Integer> termOptions) {
        this.termOptions = termOptions;
    }

    public String getLoanPurposeCode() {
        return loanPurposeCode;
    }

    public void setLoanPurposeCode(String loanPurposeCode) {
        this.loanPurposeCode = loanPurposeCode;
    }

    public String getBankCardNumber() {
        return bankCardNumber;
    }

    public void setBankCardNumber(String bankCardNumber) {
        this.bankCardNumber = bankCardNumber;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getWorkFlowCode() {
        return workFlowCode;
    }

    public void setWorkFlowCode(String workFlowCode) {
        this.workFlowCode = workFlowCode;
    }

    public String getWorkFlowFlag() {
        return workFlowFlag;
    }

    public void setWorkFlowFlag(String workFlowFlag) {
        this.workFlowFlag = workFlowFlag;
    }

    public Integer getMessageType() {
        return messageType;
    }

    public void setMessageType(Integer messageType) {
        this.messageType = messageType;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getCurrentPhone() {
        return currentPhone;
    }

    public void setCurrentPhone(String currentPhone) {
        this.currentPhone = currentPhone;
    }

    public String getRegisterPhone() {
        return registerPhone;
    }

    public void setRegisterPhone(String registerPhone) {
        this.registerPhone = registerPhone;
    }

    public String getContractNum() {
        return contractNum;
    }

    public void setContractNum(String contractNum) {
        this.contractNum = contractNum;
    }

    public String getContractStatus() {
        return contractStatus;
    }

    public void setContractStatus(String contractStatus) {
        this.contractStatus = contractStatus;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getBankCarCode() {
        return bankCarCode;
    }

    public void setBankCarCode(String bankCarCode) {
        this.bankCarCode = bankCarCode;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }


    @Override
    public String toString() {
        return "UserContext{" +
                "userName='" + userName + '\'' +
                ", availableCredit=" + availableCredit +
                ", authorized=" + authorized +
                '}';
    }
}
