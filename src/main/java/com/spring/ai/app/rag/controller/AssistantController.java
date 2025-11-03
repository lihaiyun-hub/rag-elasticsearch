package com.spring.ai.app.rag.controller;

import com.spring.ai.app.rag.chat.ChatMemory;
import com.spring.ai.app.rag.model.ChatRequest;
import com.spring.ai.app.rag.model.Message;
import com.spring.ai.app.rag.model.UserContext;
import com.spring.ai.app.rag.services.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private static final Logger logger = LoggerFactory.getLogger(AssistantController.class);

    private final ChatService chatService;

    private final ChatMemory chatMemory;

    public AssistantController(ChatService chatService,

                               ChatMemory chatMemory) {
        this.chatService = chatService;

        this.chatMemory = chatMemory;
    }

    @PostMapping(path="/chat")
    public String chat(@RequestBody ChatRequest request) {
        try {
            UserContext userContext = new UserContext();
            userContext.setTenantCode(request.getTenantCode());
            userContext.setWorkFlowFlag(request.getWorkFlowFlag());
            userContext.setMessageType(request.getMessageType());
            userContext.setUserId(request.getUserId());
            userContext.setSessionId(request.getSessionId());
            userContext.setUuid(request.getUuid());

            // Flattened from Profile
            if (request.getProfile() != null) {
                userContext.setUserName(request.getProfile().getRealName());
                userContext.setGender(request.getProfile().getGender());
                userContext.setCurrentPhone(request.getProfile().getCurrentPhone());
                userContext.setRegisterPhone(request.getProfile().getRegisterPhone());
            }

            // Flattened from LoanInfo
            if (request.getLoanInfo() != null) {
                userContext.setAvailableCredit(Double.valueOf(request.getLoanInfo().getMaxPrice()));
                userContext.setTermOptions(request.getLoanInfo().getTerms());
                userContext.setLoanPurposes(List.of(request.getLoanInfo().getLoanPurseCode()));
                if (request.getLoanInfo().getAccounts() != null && !request.getLoanInfo().getAccounts().isEmpty()) {
                    userContext.setBankCardNumber(request.getLoanInfo().getAccounts().get(0).get("cardNo"));
                    userContext.setBankName(request.getLoanInfo().getAccounts().get(0).get("bankName"));
                }
                userContext.setContractNum(request.getLoanInfo().getContractNum());
            }

            String response = chatService.chat(
                request.getSessionId(),
                request.getQuery(),
                userContext
            );
            return response;
        } catch (Exception e) {
            logger.error("AssistantController chat failed", e);
            return "抱歉，当前服务繁忙或工具调用出现问题，请稍后重试。";
        }
    }

    /**
     * 获取指定会话的历史消息
     * GET /api/assistant/history?conversationId=xxx
     */
    @GetMapping("/history")
    public List<Message> getHistory(@RequestParam String conversationId) {
        return chatMemory.get(conversationId);
    }

    /**
     * 清空指定会话的历史消息
     * DELETE /api/assistant/history?conversationId=xxx
     */
    @DeleteMapping("/history")
    public String clearHistory(@RequestParam String conversationId) {
        chatMemory.clear(conversationId);
        return "ok";
    }

    
}
