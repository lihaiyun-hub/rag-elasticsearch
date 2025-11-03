package com.spring.ai.app.rag.model;

import java.util.List;

/**
 * 用户上下文信息
 * 用于存储贷款智能助手所需的用户相关信息
 */
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private List<String> loanPurposes;
    private String bankCardNumber;
    private String bankName;

    private String tenantCode; // 租户编码
    private String workFlowFlag;  // 授信/借款流程标识
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

    public UserContext() {
        // 默认值
        this.userName = "尊敬的客户";
        this.availableCredit = 10000.0;
        this.authorized = null;
    }

    public UserContext(String userName, Double availableCredit,
                       String recentRepaymentStatus) {
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

    public List<String> getLoanPurposes() {
        return loanPurposes;
    }

    public void setLoanPurposes(List<String> loanPurposes) {
        this.loanPurposes = loanPurposes;
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



    @Override
    public String toString() {
        return "UserContext{" +
                "userName='" + userName + '\'' +
                ", availableCredit=" + availableCredit +
                ", authorized=" + authorized +
                '}';
    }
}