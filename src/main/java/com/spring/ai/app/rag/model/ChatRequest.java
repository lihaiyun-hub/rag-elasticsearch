package com.spring.ai.app.rag.model;

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
    private String currentLoanPlan;
    private String recentRepaymentStatus;
    private Double maxLoanAmount;
    private Boolean authorized;

    public ChatRequest() {
    }

    public ChatRequest(String chatId,
                       String userMessage,
                       String userName,
                       Double availableCredit,
                       String currentLoanPlan,
                       String recentRepaymentStatus,
                       Double maxLoanAmount) {
        this.chatId = chatId;
        this.userMessage = userMessage;
        this.userName = userName;
        this.availableCredit = availableCredit;
        this.currentLoanPlan = currentLoanPlan;
        this.recentRepaymentStatus = recentRepaymentStatus;
        this.maxLoanAmount = maxLoanAmount;
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

    public String getCurrentLoanPlan() {
        return currentLoanPlan;
    }

    public void setCurrentLoanPlan(String currentLoanPlan) {
        this.currentLoanPlan = currentLoanPlan;
    }

    public String getRecentRepaymentStatus() {
        return recentRepaymentStatus;
    }

    public void setRecentRepaymentStatus(String recentRepaymentStatus) {
        this.recentRepaymentStatus = recentRepaymentStatus;
    }

    public Double getMaxLoanAmount() {
        return maxLoanAmount;
    }

    public void setMaxLoanAmount(Double maxLoanAmount) {
        this.maxLoanAmount = maxLoanAmount;
    }
    public Boolean getAuthorized() {
        return authorized;
    }

    public void setAuthorized(Boolean authorized) {
        this.authorized = authorized;
    }
}