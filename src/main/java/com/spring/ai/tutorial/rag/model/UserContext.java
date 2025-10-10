/*
 * Copyright 2024-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.spring.ai.tutorial.rag.model;

/**
 * 用户上下文信息
 * 用于存储贷款智能助手所需的用户相关信息
 */
public class UserContext {
    
    private String userName;
    private double availableCredit;
    private String currentLoanPlan;
    private String recentRepaymentStatus;
    private double maxLoanAmount;
    
    public UserContext() {
        // 默认值
        this.userName = "尊敬的客户";
        this.availableCredit = 10000.0;
        this.currentLoanPlan = "无";
        this.recentRepaymentStatus = "正常";
        this.maxLoanAmount = 50000.0;
    }
    
    public UserContext(String userName, double availableCredit, String currentLoanPlan, 
                      String recentRepaymentStatus, double maxLoanAmount) {
        this.userName = userName;
        this.availableCredit = availableCredit;
        this.currentLoanPlan = currentLoanPlan;
        this.recentRepaymentStatus = recentRepaymentStatus;
        this.maxLoanAmount = maxLoanAmount;
    }
    
    // Getters and Setters
    public String getUserName() {
        return userName;
    }
    
    public void setUserName(String userName) {
        this.userName = userName;
    }
    
    public double getAvailableCredit() {
        return availableCredit;
    }
    
    public void setAvailableCredit(double availableCredit) {
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
    
    public double getMaxLoanAmount() {
        return maxLoanAmount;
    }
    
    public void setMaxLoanAmount(double maxLoanAmount) {
        this.maxLoanAmount = maxLoanAmount;
    }
    
    @Override
    public String toString() {
        return "UserContext{" +
                "userName='" + userName + '\'' +
                ", availableCredit=" + availableCredit +
                ", currentLoanPlan='" + currentLoanPlan + '\'' +
                ", recentRepaymentStatus='" + recentRepaymentStatus + '\'' +
                ", maxLoanAmount=" + maxLoanAmount +
                '}';
    }
}