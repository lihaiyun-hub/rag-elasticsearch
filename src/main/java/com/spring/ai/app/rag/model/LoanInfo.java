package com.spring.ai.app.rag.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * @author LHY
 * @date 2025-10-30 16:44
 * @description
 */
@Data
@NoArgsConstructor
public class LoanInfo {
    private String maxPrice;  // 最大贷款额度
    private String contractNum;  // 合同编号
    private String contractStatus;  // 合同状态
    private List<Integer> terms;  // 可选分期选项
    private List<Map<String, String>> accounts;  // 银行卡账户

    // 新添加字段兼容messageType=5
    private String loanPurseCode;  // 借款用途
    private String price;
    private String bankCarCode;  // 银行卡编号
    private String term;  // 分期期数
}
