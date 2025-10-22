package com.spring.ai.app.rag.model;

import java.util.List;

/**
 * 聊天请求体
 * 用于承载聊天必要参数和可选的用户上下文信息
 */
public class ChatRequest {

    private String chatId;
    private String userMessage;

    // 可选的用户上下文字段
    private String userName;
    private Double availableCredit;
    // currentLoanPlan 字段已移除
    private String recentRepaymentStatus;
    private Boolean authorized;
    // 新增：前端传入的可选期数与银行卡信息（用于生成借款方案）
    private List<Integer> termOptions;
    // 新增：前端传入的借款用途选项（用于系统提示词动态范围）
    private List<String> loanPurposes;
    private String bankCardNumber;
    private String bankName;

    public ChatRequest() {
    }

    public ChatRequest(String chatId,
                       String userMessage,
                       String userName,
                       Double availableCredit,
                       String recentRepaymentStatus) {
        this.chatId = chatId;
        this.userMessage = userMessage;
        this.userName = userName;
        this.availableCredit = availableCredit;
        this.recentRepaymentStatus = recentRepaymentStatus;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

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
}