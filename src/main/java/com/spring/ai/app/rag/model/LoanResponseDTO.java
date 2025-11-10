package com.spring.ai.app.rag.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 借款卡片返回数据
 */
@Data
public class LoanResponseDTO implements Serializable {
    private String loanPurseCode; // 借款用途代码
    private String price;        // 推荐借款金额
    private String term;         // 推荐期数
    private String bankCardNo;   // 银行卡号
}

