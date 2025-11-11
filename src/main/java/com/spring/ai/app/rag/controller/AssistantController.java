package com.spring.ai.app.rag.controller;

import com.spring.ai.app.rag.cache.RedisHashCache;
import com.spring.ai.app.rag.chat.ChatMemory;
import com.spring.ai.app.rag.model.*;
import com.spring.ai.app.rag.services.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private static final Logger logger = LoggerFactory.getLogger(AssistantController.class);

    private final ChatService chatService;

    private final ChatMemory chatMemory;
    private final RedisHashCache redisHashCache;

    public AssistantController(ChatService chatService,
                               ChatMemory chatMemory,
                               RedisHashCache redisHashCache) {
        this.chatService = chatService;
        this.chatMemory = chatMemory;
        this.redisHashCache = redisHashCache;
    }

    @PostMapping(path="/chat")
    public ChatVO chat(@RequestBody ChatRequest request) {
        try {
            UserContext userContext = buildUserContext(request);
            ChatVO vo = chatService.chat(request.getQuery(), userContext);
            return vo;
        } catch (Exception e) {
            logger.error("AssistantController chat failed", e);
            ChatVO vo = new ChatVO();
            vo.setTypeCode(2);
            vo.setContent("抱歉，当前服务繁忙或工具调用出现问题，请稍后重试。");
            return vo;
        }
    }

    private UserContext buildUserContext(ChatRequest request) {
        UserContext ctx = new UserContext();
        ctx.setTenantCode(request.getTenantCode());
        ctx.setWorkFlowFlag(request.getWorkFlowFlag());
        ctx.setWorkFlowCode(request.getWorkFlowCode());
        ctx.setMessageType(request.getMessageType());
        ctx.setUserId(request.getUserId());
        ctx.setSessionId(request.getSessionId());
        ctx.setUuid(request.getUuid());

        // 根据流程标识推导授权状态（显式填充，便于下游直接读取）
        String wf = request.getWorkFlowFlag();
        if (wf != null) {
            wf = wf.trim();
            if ("01".equals(wf)) {
                ctx.setAuthorized(false);
            } else if ("02".equals(wf)) {
                ctx.setAuthorized(true);
            }
            // 其他值保持为 null，表示未显式提供
        }

        Profile profile = request.getProfile();
        if (profile != null) {
            ctx.setUserName(profile.getRealName());
            ctx.setGender(profile.getGender());
            ctx.setCurrentPhone(profile.getCurrentPhone());
            ctx.setRegisterPhone(profile.getRegisterPhone());
        }

        LoanInfo loanInfo = request.getLoanInfo();
        if (loanInfo != null) {
            String maxPrice = loanInfo.getMaxPrice();
            if (maxPrice != null && !maxPrice.isBlank()) {
                try {
                    ctx.setAvailableCredit(Double.valueOf(maxPrice));
                } catch (Exception ignore) {
                }
            }
            ctx.setTermOptions(loanInfo.getTerms());
            String purpose = loanInfo.getLoanPurseCode();
            if (purpose != null) {
                ctx.setLoanPurposeCode(loanInfo.getLoanPurseCode());
            }
            // 直接映射 LoanInfo 其他字段
            ctx.setContractStatus(loanInfo.getContractStatus());
            ctx.setPrice(loanInfo.getPrice());
            ctx.setBankCarCode(loanInfo.getBankCarCode());
            ctx.setTerm(loanInfo.getTerm());

            java.util.List<java.util.Map<String, String>> accounts = loanInfo.getAccounts();
            if (accounts != null && !accounts.isEmpty()) {
                java.util.Map<String, String> a0 = accounts.get(0);
                if (a0 != null) {
                    ctx.setBankCardNumber(a0.get("cardNo"));
                    ctx.setBankName(a0.get("bankName"));
                }
            }
            ctx.setContractNum(loanInfo.getContractNum());

            // 写入 LoanInfo 到 Redis（统一Hash），键使用 storageId（租户前缀会话键）
            String tenant = request.getTenantCode();
            String sessionId = request.getSessionId();
            String storageId = (tenant != null && !tenant.isBlank()) ? (tenant + ":" + sessionId) : sessionId;
            String key = "loaninfo:" + storageId;
            Map<String, String> map = new HashMap<>();
            if (loanInfo.getMaxPrice() != null) map.put("maxPrice", loanInfo.getMaxPrice());
            if (loanInfo.getContractNum() != null) map.put("contractNum", loanInfo.getContractNum());
            if (loanInfo.getContractStatus() != null) map.put("contractStatus", loanInfo.getContractStatus());
            if (loanInfo.getLoanPurseCode() != null) map.put("loanPurseCode", loanInfo.getLoanPurseCode());
            if (loanInfo.getPrice() != null) map.put("price", loanInfo.getPrice());
            if (loanInfo.getBankCarCode() != null) map.put("bankCarCode", loanInfo.getBankCarCode());
            if (loanInfo.getTerm() != null) map.put("term", loanInfo.getTerm());
            redisHashCache.hPutAll(key, map);
            if (loanInfo.getTerms() != null) {
                redisHashCache.hPutJson(key, "terms_json", loanInfo.getTerms());
            }
            if (loanInfo.getAccounts() != null) {
                redisHashCache.hPutJson(key, "accounts_json", loanInfo.getAccounts());
            }
        }

        warnIfUnmapped(request);
        return ctx;
    }

    private void warnIfUnmapped(ChatRequest request) {
        // 已全部映射关键信息，无需额外告警
    }

    /**
     * 获取指定会话的历史消息
     * GET /api/assistant/history?conversationId=xxx
     */
    @GetMapping("/history")
    public List<Message> getHistory(@RequestParam String conversationId,
                                    @RequestParam(required = false) String tenantCode) {
        String storageId = (tenantCode != null && !tenantCode.isBlank()) ? (tenantCode + ":" + conversationId) : conversationId;
        return chatMemory.get(storageId);
    }

    /**
     * 清空指定会话的历史消息
     * DELETE /api/assistant/history?conversationId=xxx
     */
    @DeleteMapping("/history")
    public String clearHistory(@RequestParam String conversationId,
                               @RequestParam(required = false) String tenantCode) {
        String storageId = (tenantCode != null && !tenantCode.isBlank()) ? (tenantCode + ":" + conversationId) : conversationId;
        chatMemory.clear(storageId);
        return "ok";
    }

    
       
}
