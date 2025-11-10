package com.spring.ai.app.rag.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 接口统一返回结构
 * 0_都不返回 1_卡片信息 2_只内容 3_下一步指令 + 话术
 */
@Data
@NoArgsConstructor
public class ChatVO implements Serializable {
    private Integer typeCode;        // 0:无 1:卡片 2:内容 3:指令+话术
    private String content;          // 文本内容或话术
    private String nextWorkFlowCode; // 下一步流程代码
    private LoanResponseDTO loanInfo; // 借款卡片数据

    public ChatVO(String content) {
        this.content = content;
    }
}



