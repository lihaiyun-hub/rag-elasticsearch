package com.spring.ai.app.rag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.ai.app.rag.flow.ChatResponse;
import com.spring.ai.app.rag.flow.IntentResult;
import com.spring.ai.app.rag.flow.IntentRouter;
import com.spring.ai.app.rag.services.ConsumerCreditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/consumer-credit")
public class ConsumerCreditController {

    private final ConsumerCreditService consumerCreditService;
    private final IntentRouter intentRouter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConsumerCreditController(ConsumerCreditService consumerCreditService, IntentRouter intentRouter) {
        this.consumerCreditService = consumerCreditService;
        this.intentRouter = intentRouter;
    }

    /**
     * 前端完成某一步后回调：
     * POST /api/consumer-credit/step
     * Body: {"chatId":"xxx","step":0}
     * 返回下一步卡片或授信完成卡片
     */
    @PostMapping("/step")
    public String completeStep(@RequestBody Map<String,Object> body) throws Exception {
        log.info("completeStep body={}", body);
        String chatId = (String) body.get("chatId");
        int step = (int) body.get("step");

        // 校验步骤是否合法
        if (step < 0 || step > 7) {
            return objectMapper.writeValueAsString(new ChatResponse("error","非法步骤","ERROR", ""));
        }

        // 完成当前步骤（状态机模式）
        consumerCreditService.completeStep(chatId, step);

        // 检查是否全部完成
        if (consumerCreditService.getCurrentStep(chatId) >= 8) {
            // 授信完成，标记授权并返回借款方案
            consumerCreditService.markAuthorized(chatId);
            // 使用状态机返回完成卡片
            IntentResult ir = new IntentResult("CONSUMER_CREDIT", 0.9, Map.of(), null);
            ChatResponse response = intentRouter.route(chatId, ir);
            return objectMapper.writeValueAsString(response);
        }

        // 使用状态机返回下一步卡片
        IntentResult ir = new IntentResult("CONSUMER_CREDIT", 0.9, Map.of("current_step", String.valueOf(step)), null);
        ChatResponse response = intentRouter.route(chatId, ir);
        return objectMapper.writeValueAsString(response);
    }

    /**
     * 查询授信状态：
     * GET /api/consumer-credit/status?chatId=xxx
     */
    @GetMapping("/status")
    public Map<String,Object> status(@RequestParam String chatId) {
        try {
            Map<String,Object> result = new HashMap<>();
            result.put("authorized", consumerCreditService.isAuthorized(chatId));
            result.put("currentStep", consumerCreditService.getCurrentStep(chatId));
            return result;
        } catch (Exception e) {
            log.error("status error", e);
            throw e;
        }
    }
    
    @PostMapping("/authorize")
    public Map<String, Object> configureAuthorization(@RequestBody Map<String, Object> body) {
        String chatId = (String) body.get("chatId");
        Object authObj = body.get("authorized");
        boolean authorized = false;
        if (authObj instanceof Boolean) {
            authorized = (Boolean) authObj;
        } else if (authObj instanceof String) {
            authorized = Boolean.parseBoolean((String) authObj);
        } else if (authObj instanceof Number) {
            authorized = ((Number) authObj).intValue() != 0;
        }
        consumerCreditService.setAuthorized(chatId, authorized);
        Map<String,Object> result = new HashMap<>();
        result.put("authorized", consumerCreditService.isAuthorized(chatId));
        result.put("currentStep", consumerCreditService.getCurrentStep(chatId));
        return result;
    }
}