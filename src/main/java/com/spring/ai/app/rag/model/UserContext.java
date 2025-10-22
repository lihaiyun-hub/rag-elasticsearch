package com.spring.ai.app.rag.model;

import java.util.List;

/**
 * 用户上下文信息
 * 用于存储贷款智能助手所需的用户相关信息
 */
public class UserContext {

    private String userName;
    private Double availableCredit;
    // currentLoanPlan 字段已移除
    private String recentRepaymentStatus;
    private Boolean authorized; // 授信状态：null 表示未提供；true/false 表示显式状态
    private List<Integer> termOptions;
    private List<String> loanPurposes;
    private String bankCardNumber;
    private String bankName;

    public UserContext() {
        // 默认值
        this.userName = "尊敬的客户";
        this.availableCredit = 10000.0;
        this.recentRepaymentStatus = "正常";
        this.authorized = null;
    }

    public UserContext(String userName, Double availableCredit,
                       String recentRepaymentStatus) {
        this.userName = userName;
        this.availableCredit = availableCredit;
        this.recentRepaymentStatus = recentRepaymentStatus;
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


    public String getRecentRepaymentStatus() {
        return recentRepaymentStatus;
    }

    public void setRecentRepaymentStatus(String recentRepaymentStatus) {
        this.recentRepaymentStatus = recentRepaymentStatus;
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

    @Override
    public String toString() {
        return "UserContext{" +
                "userName='" + userName + '\'' +
                ", availableCredit=" + availableCredit +
                ", recentRepaymentStatus='" + recentRepaymentStatus + '\'' +
                ", authorized=" + authorized +
                '}';
    }
}