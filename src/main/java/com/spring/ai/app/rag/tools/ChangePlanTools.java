package com.spring.ai.app.rag.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;



public class ChangePlanTools {

    private static final Logger logger = LoggerFactory.getLogger(ChangePlanTools.class);

    @Tool(description = "Change the loan plan, allowing independent changes to the loan amount or the number of installments.")
    public String changeLoanPlanMethod(@ToolParam( description = "借款金额",required = false) String loanAmount,
                                       @ToolParam( description = "分期数",required = false) String installments) {
        logger.info("The new loan plan is {} for customer {}", loanAmount, installments);

        return String.format("您的借款方案已更新：借款金额%s元，分期%s期", loanAmount, installments);
    }

    @Tool(description = "Apply for a loan with specified amount, installments and purpose")
    public String applyForLoan(@ToolParam(description = "借款金额", required = false) String loanAmount,
                              @ToolParam(description = "分期期数", required = false) String installments,
                              @ToolParam(description = "借款用途", required = false) String purpose) {
        logger.info("Loan application: amount={}, installments={}, purpose={}", loanAmount, installments, purpose);
        
        // 验证借款参数
        if (!isValidLoanAmount(loanAmount)) {
            return "借款金额需在100-50000元之间，且为100的倍数";
        }
        
        if (!isValidInstallments(installments)) {
            return "分期期数仅支持：3/6/9/12/18/24期";
        }
        
        if (!isValidPurpose(purpose)) {
            return "借款用途支持：日常消费、教育培训、医疗健康、家庭装修、旅游出行、数码家电、其他";
        }
        
        return String.format("借款申请已提交：金额%s元，分期%s期，用途：%s。请等待审核结果。", loanAmount, installments, purpose);
    }

    @Tool(description = "Query available credit limit for the user")
    public String queryCreditLimit() {
        logger.info("Querying credit limit for user");
        return "您的当前可用额度为10000元，最高可借款50000元。";
    }

    @Tool(description = "Query current loan plan details")
    public String queryCurrentLoanPlan() {
        logger.info("Querying current loan plan");
        return "您当前无进行中的借款计划。";
    }

    @Tool(description = "Calculate loan interest and monthly payment")
    public String calculateLoanInterest(@ToolParam(description = "借款金额", required = true) String loanAmount,
                                       @ToolParam(description = "分期期数", required = true) String installments) {
        logger.info("Calculating interest for loan: amount={}, installments={}", loanAmount, installments);
        
        try {
            double amount = Double.parseDouble(loanAmount);
            int periods = Integer.parseInt(installments);
            
            // 简化的利息计算（实际应该调用真实的计算服务）
            double annualRate = 0.12; // 年利率12%
            double monthlyRate = annualRate / 12;
            
            double totalInterest = amount * monthlyRate * periods;
            double monthlyPayment = (amount + totalInterest) / periods;
            
            return String.format("借款%s元分%s期，总利息约%.2f元，每月还款约%.2f元（年化利率12%%）", 
                               loanAmount, installments, totalInterest, monthlyPayment);
        } catch (NumberFormatException e) {
            return "请输入有效的借款金额和期数";
        }
    }

    // 验证方法
    private boolean isValidLoanAmount(String amount) {
        try {
            double value = Double.parseDouble(amount);
            return value >= 100 && value <= 50000 && (value % 100 == 0);
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private boolean isValidInstallments(String installments) {
        return installments.matches("^(3|6|9|12|18|24)$");
    }
    
    private boolean isValidPurpose(String purpose) {
        String[] validPurposes = {
            "日常消费", "教育培训", "医疗健康", "家庭装修", "旅游出行", "数码家电", "其他"
        };
        return java.util.Arrays.asList(validPurposes).contains(purpose);
    }
}
