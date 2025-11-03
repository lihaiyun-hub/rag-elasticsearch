package com.spring.ai.app.rag.model;

import lombok.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * 聊天请求体
 * 用于承载聊天必要参数和可选的用户上下文信息
 */
@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    private String tenantCode; // 租户编码
    private String workFlowFlag;  // 授信/借款流程标识
    private Integer messageType;  // 消息处理类型
    private String query;  // 用户查询
    private String userId;  // 用户ID
    private String sessionId;  // 会话ID
    private String uuid;  // 唯一标识
    private LoanInfo loanInfo;  // 贷款信息
    private Profile profile;  // 用户信息


}