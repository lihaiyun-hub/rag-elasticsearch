package com.spring.ai.app.rag.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.ai.app.rag.flow.ChatResponse;
import com.spring.ai.app.rag.flow.ConsumerCreditStateMachine;
import com.spring.ai.app.rag.flow.IntentResult;
import com.spring.ai.app.rag.model.UserContext;
import com.spring.ai.app.rag.services.ConsumerCreditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ConsumerLoanTools {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerLoanTools.class);
    private final ObjectMapper objectMapper;
    private final ConsumerCreditService consumerCreditService;
    private final ConsumerCreditStateMachine consumerCreditStateMachine;
    private final ChatMemory chatMemory;

    @Autowired
    public ConsumerLoanTools(ObjectMapper objectMapper,
                           ConsumerCreditService consumerCreditService, 
                           ConsumerCreditStateMachine consumerCreditStateMachine,
                           ChatMemory chatMemory) {
        this.objectMapper = objectMapper;
        this.consumerCreditService = consumerCreditService;
        this.consumerCreditStateMachine = consumerCreditStateMachine;
        this.chatMemory = chatMemory;
    }

    /**
     * 启动消费贷授信流程
     * 当用户表达借款意愿但未授信时调用
     */
    @Tool(description = "Start consumer credit authorization process when user wants to apply for loan but is not authorized yet", returnDirect = true)
    public String startConsumerCreditProcess(
            @ToolParam(description = "用户ID", required = true) String userId,
            @ToolParam(description = "用户上下文信息，包含用户名、可用额度等", required = false) UserContext userContext) {
        
        logger.info("Starting consumer credit process for user: {}", userId);
        
        try {
            // 初始化用户数据
            consumerCreditService.initIfAbsent(userId);
            
            // 创建意图结果，启动授信流程
            IntentResult intentResult = new IntentResult("CONSUMER_CREDIT", 0.9, Map.of(), null);
            ChatResponse response = consumerCreditStateMachine.start(userId, intentResult);
            
            // 转换为字符串返回
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.error("Failed to start consumer credit process for user: {}", userId, e);
            return "{\"error\":\"启动授信流程失败，请稍后再试\"}";
        }
    }

    /**
     * 为已授信用户生成借款方案
     * 当用户已授信并表达借款意愿时调用
     * 支持参数化生成：可指定金额、期数、用途等参数
     */
    @Tool(description = "Generate loan offers for authorized users who want to apply for loan", returnDirect = true)
    public String generateLoanOffers(
            @ToolParam(description = "用户ID", required = true) String userId,
            @ToolParam(description = "用户上下文信息", required = false) UserContext userContext,
            @ToolParam(description = "借款金额，可选参数", required = false) Double amount,
            @ToolParam(description = "借款期数（月），可选参数", required = false) Integer termMonths,
            @ToolParam(description = "借款用途，可选参数", required = false) String purpose) {
        
        logger.info("Generating loan offers for authorized user: {}, amount: {}, term: {}, purpose: {}", 
                   userId, amount, termMonths, purpose);
        
        try {
            // 构建借款方案
            Map<String, String> payload = new HashMap<>();
            payload.put("action", "confirm_loan");
            payload.put("label", "确认借款");
            payload.put("change_action", "change_plan");
            payload.put("change_label", "更换方案");
            
            // 生成参数化的借款方案
            String offer = buildParameterizedOffer(userContext, amount, termMonths, purpose);
            payload.put("offer", offer);
            
            // 创建卡片响应
            ChatResponse response = new ChatResponse(
                "card_list",
                java.util.List.of(new com.spring.ai.app.rag.flow.Card(
                    "consumer_loan_offers", 
                    "为您推荐以下消费贷方案", 
                    "多款额度可选，随借随还",
                    "intent",
                    payload
                )),
                "CREDIT_DONE",
                UUID.randomUUID().toString()
            );
            
            // 向ChatMemory写入系统消息，告知大模型已经推送借款方案
            SystemMessage systemMessage = new SystemMessage("已为" + userId + "用户成功推送借款方案，方案详情：" + offer);
            chatMemory.add(userId, List.of(systemMessage));
            
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.error("Failed to generate loan offers for user: {}", userId, e);
            return "{\"error\":\"生成借款方案失败，请稍后再试\"}";
        }
    }



    /**
     * 构建参数化借款方案
     */
    private String buildParameterizedOffer(UserContext userContext, Double amount, Integer termMonths, String purpose) {
        // 获取用户可用额度
        double availableCredit = 15000.0; // 默认额度
        if (userContext != null) {
            availableCredit = userContext.getAvailableCredit();
        }
        
        // 确定借款金额
        double finalAmount = amount != null ? amount : 10000.0;
        
        // 确保借款金额不超过可用额度
        if (finalAmount > availableCredit) {
            finalAmount = availableCredit;
        }
        
        // 确定借款期限
        int finalTerm = termMonths != null ? termMonths : 12;
        
        // 确定年利率（模拟）
        double annualRate = 0.045; // 4.5% 年利率
        
        // 计算月供（等额本息）
        double monthlyRate = annualRate / 12;
        double monthlyPayment = (finalAmount * monthlyRate * Math.pow(1 + monthlyRate, finalTerm)) / 
                               (Math.pow(1 + monthlyRate, finalTerm) - 1);
        
        // 计算总利息
        double totalInterest = (monthlyPayment * finalTerm) - finalAmount;
        
        // 构建结构化数据对象
        Map<String, Object> offerData = new HashMap<>();
        offerData.put("id", 1);
        offerData.put("amount", (int)finalAmount);
        offerData.put("rate", annualRate * 100); // 转换为百分比
        offerData.put("term", finalTerm);
        offerData.put("monthlyPayment", (int)monthlyPayment);
        offerData.put("totalInterest", (int)totalInterest);
        offerData.put("tag", "推荐");
        offerData.put("bankName", "建设银行");
        offerData.put("repayMode", "等额本息");
        offerData.put("purpose", purpose != null ? purpose : "个人消费");
        offerData.put("bankTail", "1123");
        offerData.put("discountText", "查看优惠");
        offerData.put("firstPayment", (int)monthlyPayment);
        offerData.put("lender", "建设银行");
        offerData.put("annualRate", annualRate * 100);
        
        try {
            return objectMapper.writeValueAsString(offerData);
        } catch (Exception e) {
            logger.error("Failed to serialize offer data", e);
            // 降级为字符串格式
            StringBuilder offerBuilder = new StringBuilder();
            offerBuilder.append("**借款金额:** ¥").append(String.format("%,.0f", finalAmount)).append("\n");
            offerBuilder.append("**借款期限:** ").append(finalTerm).append("个月\n");
            offerBuilder.append("**年利率:** ").append(String.format("%.1f", annualRate * 100)).append("%\n");
            offerBuilder.append("**月供:** ¥").append(String.format("%,.0f", monthlyPayment)).append("\n");
            offerBuilder.append(" **总利息:** ¥").append(String.format("%,.0f", totalInterest)).append("\n");
            
            if (purpose != null) {
                offerBuilder.append("**借款用途:** ").append(purpose).append("\n");
            }
            
            offerBuilder.append("**推荐银行:** 建设银行\n");
            offerBuilder.append("**还款方式:** 等额本息");
            
            return offerBuilder.toString();
        }
    }

}