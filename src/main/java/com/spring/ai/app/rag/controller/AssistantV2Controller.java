package com.spring.ai.app.rag.controller;

import com.spring.ai.app.rag.services.CustomerSupportAssistantV2;
import com.spring.ai.app.rag.model.UserContext;
import com.spring.ai.app.rag.model.ChatRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 助手控制器 V2
 * 支持分离的RAG架构，提供更灵活的检索和大模型调用策略
 * 
 * @author LHY
 * @date 2025-01-20
 */
@RequestMapping("/api/v2/assistant")
@RestController
public class AssistantV2Controller {

    private final CustomerSupportAssistantV2 assistantV2;
    private final com.spring.ai.app.rag.services.ConsumerCreditService consumerCreditService;

    private static final Logger logger = LoggerFactory.getLogger(AssistantV2Controller.class);

    public AssistantV2Controller(CustomerSupportAssistantV2 assistantV2, 
                                com.spring.ai.app.rag.services.ConsumerCreditService consumerCreditService) {
        this.assistantV2 = assistantV2;
        this.consumerCreditService = consumerCreditService;
    }

    /**
     * 聊天接口 - 使用分离的RAG架构
     */
    @PostMapping(path="/chat")
    public String chat(@RequestBody ChatRequest request) {
        try {
            // 构建用户上下文（允许为空，后端会使用默认值）
            UserContext userContext = buildUserContext(request);

            String response = assistantV2.chat(
                request.getChatId(),
                request.getUserMessage(),
                userContext
            );
            return response;
        } catch (Exception e) {
            logger.error("AssistantV2Controller chat failed", e);
            return "抱歉，当前服务繁忙或工具调用出现问题，请稍后重试。";
        }
    }



    /**
     * 健康检查接口
     */
    @GetMapping(path="/health")
    public String health() {
        return "AssistantV2 服务正常运行";
    }

    /**
     * 构建用户上下文
     */
    private UserContext buildUserContext(ChatRequest request) {
        UserContext userContext = new UserContext();
        
        if (request.getUserName() != null) {
            userContext.setUserName(request.getUserName());
        }
        if (request.getAvailableCredit() != null) {
            userContext.setAvailableCredit(request.getAvailableCredit());
        }
        if (request.getRecentRepaymentStatus() != null) {
            userContext.setRecentRepaymentStatus(request.getRecentRepaymentStatus());
        }
        
        // 若前端显式传递了授信状态，则覆盖服务侧的记录，并注入到用户上下文
        if (request.getAuthorized() != null) {
            // 更新服务侧记录，保证状态机与授权状态一致
            consumerCreditService.setAuthorized(request.getChatId(), request.getAuthorized());
            // 将授权状态注入到用户上下文，供后续助手逻辑直接使用
            userContext.setAuthorized(request.getAuthorized());
        }

        // 接收并传递可选分期、银行卡后四位、银行名信息到助手服务
        String bankCardNumber = request.getBankCardNumber();
        String bankName = request.getBankName();
        
        // 将 termOptions 传送到助手服务内部逻辑
        // 将画像信息注入到 UserContext
        userContext.setTermOptions(request.getTermOptions());
        userContext.setLoanPurposes(request.getLoanPurposes());
        userContext.setBankCardNumber(bankCardNumber);
        userContext.setBankName(bankName);

        return userContext;
    }
}